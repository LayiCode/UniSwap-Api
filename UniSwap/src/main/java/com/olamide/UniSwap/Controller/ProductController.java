package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.PageResponseDTO;
import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Service.FileStorageService;
import com.olamide.UniSwap.Service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductService productService;
    private final FileStorageService fileStorageService;

    // GET /api/products                          -> all available listings, page 0, size 10, newest first
    // GET /api/products?page=1&size=20            -> page 1, 20 per page
    // GET /api/products?category=Electronics      -> available listings in category X
    // GET /api/products?search=laptop             -> title search (any status)
    @GetMapping
    public ResponseEntity<PageResponseDTO<ProductDTO>> getAll(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = buildPageable(page, size);
        Page<Product> products;

        if (search != null && !search.isBlank()) {
            products = productService.search(search, pageable);
        } else if (category != null && !category.isBlank()) {
            products = productService.getByCategory(category, pageable);
        } else {
            products = productService.getAllAvailable(pageable);
        }

        return ResponseEntity.ok(toPageResponse(products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable Long id) {
        Product product = productService.getById(id);
        return ResponseEntity.ok(ProductDTO.fromEntity(product));
    }

    // Requires auth — "my listings" page, includes both AVAILABLE and SOLD.
    @GetMapping("/my-listings")
    public ResponseEntity<PageResponseDTO<ProductDTO>> getMyListings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = buildPageable(page, size);
        Page<Product> products = productService.getBySeller(principal.getId(), pageable);
        return ResponseEntity.ok(toPageResponse(products));
    }

    @PostMapping
    public ResponseEntity<ProductDTO> create(
            @Valid @RequestBody ProductDTO dto,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product created = productService.create(dto, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDTO.fromEntity(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductDTO dto,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product updated = productService.update(id, dto, principal.getId());
        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }

    // multipart/form-data, not JSON — the field name must be "file".
    // Seller-only: ownership is enforced inside productService.updateImage.
    @PostMapping("/{id}/image")
    public ResponseEntity<ProductDTO> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String imageUrl = fileStorageService.store(file);
        Product updated = productService.updateImage(id, imageUrl, principal.getId());
        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }

    @PatchMapping("/{id}/sold")
    public ResponseEntity<ProductDTO> markAsSold(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product sold = productService.markAsSold(id, principal.getId());
        return ResponseEntity.ok(ProductDTO.fromEntity(sold));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        productService.delete(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // Caps page size server-side — a client sending ?size=100000 can't force
    // the DB to load an enormous result set. Newest-first by default since
    // that's the natural browse order for a marketplace feed.
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private PageResponseDTO<ProductDTO> toPageResponse(Page<Product> products) {
        return PageResponseDTO.from(products, products.getContent().stream().map(ProductDTO::fromEntity).toList());
    }
}