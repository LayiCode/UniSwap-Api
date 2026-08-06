package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.PageResponseDTO;
import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Every endpoint here is behind .anyRequest().authenticated() in
// SecurityConfig — a favorites list is private per-user.
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private static final int MAX_PAGE_SIZE = 50;

    private final FavoriteService favoriteService;

    // GET /api/favorites -> the authenticated user's saved listings, newest save first
    @GetMapping
    public ResponseEntity<PageResponseDTO<ProductDTO>> favorites(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        Pageable pageable = buildPageable(page, size);
        Page<Product> products = favoriteService.getFavorites(currentUserId(principal), pageable);
        return ResponseEntity.ok(toPageResponse(products));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Void> add(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        favoriteService.add(currentUserId(principal), productId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        favoriteService.remove(currentUserId(principal), productId);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }

    // No sort parameter: the feed is always newest-saved-first (the repository
    // method declares that ordering). Only the page size needs capping.
    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }

    // Everything on this feed is favorited by definition, so the flag is true
    // for every row — the frontend renders the heart filled on this page.
    private PageResponseDTO<ProductDTO> toPageResponse(Page<Product> products) {
        return PageResponseDTO.from(products, products.getContent().stream()
                .map(p -> ProductDTO.fromEntity(p, true))
                .toList());
    }
}
