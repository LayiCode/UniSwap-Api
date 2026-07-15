package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String AVAILABLE = "AVAILABLE";
    private static final String SOLD = "SOLD";

    private final ProductRepository productRepository;
    private final UserService userService;

    public Product create(ProductDTO dto, Long sellerId) {
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

    public Page<Product> getAllAvailable(Pageable pageable) {
        return productRepository.findByStatus(AVAILABLE, pageable);
    }

    public Page<Product> getByCategory(String category, Pageable pageable) {
        return productRepository.findByCategoryAndStatus(category, AVAILABLE, pageable);
    }

    public Page<Product> search(String keyword, Pageable pageable) {
        return productRepository.findByTitleContainingIgnoreCase(keyword, pageable);
    }

    public Page<Product> getBySeller(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerId(sellerId, pageable);
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public Product update(Long id, ProductDTO dto, Long requesterId) {
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

    public Product updateImage(Long id, String imageUrl, Long requesterId) {
        Product product = getById(id);
        requireOwnership(product, requesterId);

        product.setImageUrl(imageUrl);
        return productRepository.save(product);
    }

    public Product markAsSold(Long id, Long requesterId) {
        Product product = getById(id);
        requireOwnership(product, requesterId);

        product.setStatus(SOLD);
        return productRepository.save(product);
    }

    public void delete(Long id, Long requesterId) {
        Product product = getById(id);
        requireOwnership(product, requesterId);

        productRepository.delete(product);
    }

    private void requireOwnership(Product product, Long requesterId) {
        if (!product.getSeller().getId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this listing");
        }
    }
}