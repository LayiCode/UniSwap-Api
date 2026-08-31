package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.ProductImage;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    // Hard cap per listing; each file is additionally capped at 5MB by the
    // global multipart limit.
    private static final int MAX_IMAGES = 5;

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
                .location(dto.getLocation())
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

    // Public view of a user's profile page: only their currently-available
    // listings, so a buyer browsing a seller's profile only sees what they can
    // actually buy right now.
    @Transactional(readOnly = true)
    public Page<Product> getAvailableBySeller(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerIdAndStatus(sellerId, ProductStatus.AVAILABLE, pageable);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        Product product = productRepository.findByIdWithSeller(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (product.getSeller() != null && product.getSeller().isDeleted()) {
            // A deleted seller's listing no longer exists publicly.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return product;
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
        product.setLocation(dto.getLocation());
        // imageUrl/images are deliberately NOT taken from the DTO — the lean
        // frontend payload never carries them, and copying a null here would
        // silently detach every photo from the listing. Photos are managed
        // exclusively through the upload endpoints.
        // status is intentionally not editable here — use markAsSold() so
        // that transition has its own explicit, auditable entry point.

        return productRepository.save(product);
    }

    // Ownership and existence are verified BEFORE the bytes hit the disk, so
    // an attacker can't flood the upload folder by targeting ids they don't
    // own. Replacing existing photos cleans up the old files.
    @Transactional
    public Product uploadImage(Long id, MultipartFile file, Long requesterId) {
        // Arrays.asList tolerates a null element so a missing file surfaces
        // through the shared validation path instead of an NPE.
        return uploadImages(id, java.util.Arrays.asList(file), requesterId);
    }

    // Replace-all semantics: the submitted set becomes the listing's full
    // photo set (index 0 = cover). Old storage objects are removed
    // best-effort after the DB write succeeds.
    //
    // Identity first: existence and ownership are resolved before any
    // validation or byte is touched, so a non-owner always learns only one
    // thing — 403 — regardless of what they sent.
    @Transactional
    public Product uploadImages(Long id, List<MultipartFile> files, Long requesterId) {
        Product product = getById(id);
        requireOwnership(product, requesterId);

        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image is required");
        }
        if (files.size() > MAX_IMAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A listing can have at most " + MAX_IMAGES + " images");
        }

        Set<String> previousUrls = collectImageUrls(product);

        List<String> urls = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                urls.add(fileStorageService.store(file));
            }
            product.getImages().clear();
            for (int i = 0; i < urls.size(); i++) {
                product.getImages().add(ProductImage.builder()
                        .product(product)
                        .url(urls.get(i))
                        .sortOrder(i)
                        .build());
            }
            product.setImageUrl(urls.get(0));
            Product saved = productRepository.save(product);
            previousUrls.removeAll(urls);
            previousUrls.forEach(fileStorageService::delete);
            return saved;
        } catch (RuntimeException e) {
            // If anything failed, don't leave the new files orphaned.
            urls.forEach(fileStorageService::delete);
            throw e;
        }
    }

    // Every URL currently attached to the product: the image rows plus the
    // legacy cover column (older listings predate the images table).
    private Set<String> collectImageUrls(Product product) {
        Set<String> urls = new LinkedHashSet<>();
        product.getImages().forEach(img -> urls.add(img.getUrl()));
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            urls.add(product.getImageUrl());
        }
        return urls;
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

        Set<String> urls = collectImageUrls(product);
        productRepository.delete(product);
        // Best-effort: a leftover file is acceptable, a failed row delete is not.
        urls.forEach(fileStorageService::delete);
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
