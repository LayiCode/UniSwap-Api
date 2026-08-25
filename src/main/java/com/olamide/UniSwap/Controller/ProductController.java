package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.PageResponseDTO;
import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.ProductStatus;
import com.olamide.UniSwap.Service.FavoriteService;
import com.olamide.UniSwap.Service.ProductService;
import com.olamide.UniSwap.Service.PurchaseRequestService;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ProductService productService;
    private final FavoriteService favoriteService;
    private final PurchaseRequestService purchaseRequestService;

    // GET /api/products                                             -> all available listings, page 0, size 10, newest first
    // GET /api/products?page=1&size=20                               -> page 1, 20 per page
    // GET /api/products?category=Electronics                          -> available listings in category X
    // GET /api/products?search=laptop                                 -> title/description contains "laptop"
    // GET /api/products?search=laptop&category=Electronics            -> combined search + category
    // GET /api/products?condition=Brand New&minPrice=1000&maxPrice=50000
    // GET /api/products?sort=price_asc|price_desc|newest              -> sort (default newest)
    @GetMapping
    public ResponseEntity<PageResponseDTO<ProductDTO>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<Product> products = productService.searchProducts(
                search, category, condition, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(toPageResponse(products, principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product product = productService.getById(id);
        // A listing removed by moderation is hidden from everyone except the
        // seller (who still needs to see/delete it in their inventory) and
        // admins (who may want to double-check the removal).
        if (product.getStatus() == ProductStatus.REMOVED
                && !canViewRemoved(product, principal)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        ProductDTO dto = ProductDTO.fromEntity(product,
                favoriteService.isFavorited(principalUserId(principal), id));
        dto.setPurchaseRequested(purchaseRequestService.hasPendingRequest(principalUserId(principal), id));
        return ResponseEntity.ok(dto);
    }

    // Requires auth — "my listings" page, includes both AVAILABLE and SOLD.
    @GetMapping("/my-listings")
    public ResponseEntity<PageResponseDTO<ProductDTO>> getMyListings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = buildPageable(page, size);
        Page<Product> products = productService.getBySeller(currentUserId(principal), pageable);
        return ResponseEntity.ok(toPageResponse(products, principal));
    }

    @PostMapping
    public ResponseEntity<ProductDTO> create(
            @Valid @RequestBody ProductDTO dto,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product created = productService.create(dto, currentUserId(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDTO.fromEntity(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductDTO dto,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product updated = productService.update(id, dto, currentUserId(principal));
        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }

    // multipart/form-data, not JSON — the field name must be "file".
    // Seller-only: ownership is verified inside ProductService BEFORE the
    // bytes are written to disk, so a rejected upload never orphans a file.
    @PostMapping("/{id}/image")
    public ResponseEntity<ProductDTO> uploadImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product updated = productService.uploadImage(id, file, currentUserId(principal));
        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }

    // Multi-photo variant: field name "files", 1..5 images, replace-all —
    // the submitted set becomes the listing's complete photo set.
    @PostMapping("/{id}/images")
    public ResponseEntity<ProductDTO> uploadImages(
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product updated = productService.uploadImages(id, files, currentUserId(principal));
        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }

    @PatchMapping("/{id}/sold")
    public ResponseEntity<ProductDTO> markAsSold(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Product sold = productService.markAsSold(id, currentUserId(principal));
        return ResponseEntity.ok(ProductDTO.fromEntity(sold));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        productService.delete(id, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    // Defense-in-depth: a null principal means the security matcher somehow
    // let an unauthenticated request through. Fail with a clean 401 instead
    // of an NPE that would surface as a confusing 500.
    private Long currentUserId(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }

    // Anonymous viewers stay null here — browse/detail are public, and the
    // favorited flag simply defaults to false for them.
    private Long principalUserId(UserPrincipal principal) {
        return (principal == null || principal.getId() == null) ? null : principal.getId();
    }

    // True when the caller may still view a moderated-away listing. The seller
    // keeps access so they can manage/delete it; admins can audit the removal.
    private boolean canViewRemoved(Product product, UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            return false;
        }
        return principal.getUser().isAdmin()
                || product.getSeller().getId().equals(principal.getId());
    }

    // Caps page size server-side — a client sending ?size=100000 can't force
    // the DB to load an enormous result set. Newest-first by default since
    // that's the natural browse order for a marketplace feed.
    private Pageable buildPageable(int page, int size) {
        return buildPageable(page, size, "newest");
    }

    private Pageable buildPageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return switch (sort == null ? "newest" : sort) {
            case "price_asc" -> PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "price"));
            case "price_desc" -> PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "price"));
            case "newest" -> PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown sort '" + sort + "' (expected newest, price_asc or price_desc)");
        };
    }

    // Same shape as toPageResponse but no N+1: the whole page's favorited ids
    // are fetched in one query, keyed off the current viewer (null for
    // anonymous requests, in which case everything maps to false).
    private PageResponseDTO<ProductDTO> toPageResponse(Page<Product> products, UserPrincipal principal) {
        Set<Long> favorited = favoriteService.getFavoritedProductIds(
                principalUserId(principal),
                products.getContent().stream().map(Product::getId).toList());
        return PageResponseDTO.from(products, products.getContent().stream()
                .map(p -> ProductDTO.fromEntity(p, favorited.contains(p.getId())))
                .toList());
    }
}
