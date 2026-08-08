package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.ProductStatus;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    // Server-side vocabularies for the free-form-ish category/condition
    // strings. Keeps typo'd categories from silently returning empty feeds.
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "Electronics", "Phones & Tablets", "Books", "Furniture", "Clothing",
            "Shoes", "Vehicles", "Sports", "Beauty", "Others");
    private static final Set<String> ALLOWED_CONDITIONS = Set.of(
            "Brand New", "Neatly Used", "Fairly Used", "Refurbished", "For Parts");

    private final ProductRepository productRepository;
    private final UserService userService;
    private final FileStorageService fileStorageService;

    @Transactional
    public Product create(ProductDTO dto, Long sellerId) {
        validateCategoryAndCondition(dto);
        User seller = userService.getById(sellerId);

        Product product = Product.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .category(dto.getCategory())
                .itemCondition(dto.getItemCondition())
                .imageUrl(dto.getImageUrl())
                .seller(seller)
                .build();
        // status is deliberately not taken from the DTO — @PrePersist on
        // Product defaults it to AVAILABLE, so a client can't create a
        // listing that's already marked SOLD.

        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Page<Product> getAllAvailable(Pageable pageable) {
        return searchProducts(null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String keyword, String category, String condition,
                                        BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        validatePriceRange(minPrice, maxPrice);
        String pattern = (keyword == null || keyword.isBlank())
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";
        return productRepository.searchFilters(
                ProductStatus.AVAILABLE,
                pattern,
                blankToNull(category),
                blankToNull(condition),
                minPrice,
                maxPrice,
                pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> getBySeller(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productRepository.findByIdWithSeller(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @Transactional
    public Product update(Long id, ProductDTO dto, Long requesterId) {
        validateCategoryAndCondition(dto);
        Product product = getById(id);
        requireOwnership(product, requesterId);

        product.setTitle(dto.getTitle());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setCategory(dto.getCategory());
        product.setItemCondition(dto.getItemCondition());
        product.setImageUrl(dto.getImageUrl());
        // status is intentionally not editable here — use markAsSold() so
        // that transition has its own explicit, auditable entry point.

        return productRepository.save(product);
    }

    // Ownership and existence are verified BEFORE the bytes hit the disk, so
    // an attacker can't flood the upload folder by targeting ids they don't
    // own. Replacing an existing image cleans up the old file.
    @Transactional
    public Product uploadImage(Long id, MultipartFile file, Long requesterId) {
        Product product = getById(id);
        requireOwnership(product, requesterId);

        String previousUrl = product.getImageUrl();
        String imageUrl = fileStorageService.store(file);

        try {
            product.setImageUrl(imageUrl);
            Product saved = productRepository.save(product);
            if (previousUrl != null && !previousUrl.equals(imageUrl)) {
                fileStorageService.delete(previousUrl);
            }
            return saved;
        } catch (RuntimeException e) {
            // If the DB write failed, don't leave the new file orphaned.
            fileStorageService.delete(imageUrl);
            throw e;
        }
    }

    @Transactional
    public Product markAsSold(Long id, Long requesterId) {
        Product product = getById(id);
        requireOwnership(product, requesterId);

        product.setStatus(ProductStatus.SOLD);
        return productRepository.save(product);
    }

    // Status transition performed on behalf of moderation (e.g. removing a
    // reported listing). Deliberately has no ownership check — only the admin
    // report flow calls this, and that path is guarded by hasRole("ADMIN").
    @Transactional
    public Product moderateStatus(Long id, ProductStatus status) {
        Product product = getById(id);
        product.setStatus(status);
        return productRepository.save(product);
    }

    @Transactional
    public void delete(Long id, Long requesterId) {
        Product product = getById(id);
        requireOwnership(product, requesterId);

        productRepository.delete(product);
        // Best-effort: a leftover file is acceptable, a failed row delete is not.
        if (product.getImageUrl() != null) {
            fileStorageService.delete(product.getImageUrl());
        }
    }

    private void requireOwnership(Product product, Long requesterId) {
        if (!product.getSeller().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this listing");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum price cannot be negative");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum price cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Minimum price cannot exceed maximum price");
        }
    }

    private void validateCategoryAndCondition(ProductDTO dto) {
        if (!ALLOWED_CATEGORIES.contains(dto.getCategory())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category");
        }
        if (!ALLOWED_CONDITIONS.contains(dto.getItemCondition())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown condition");
        }
    }
}
