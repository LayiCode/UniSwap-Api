package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Entity.Favorite;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProductService productService;
    private final UserService userService;

    // Existence and the one-favorite-per-listing rule are checked before the
    // row is written; the DB unique constraint backs it up as a final guard.
    @Transactional
    public void add(Long userId, Long productId) {
        Product product = productService.getById(productId);
        if (favoriteRepository.existsByUser_IdAndProduct_Id(userId, productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Listing is already in favorites");
        }
        User user = userService.getById(userId);
        favoriteRepository.save(Favorite.builder().user(user).product(product).build());
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        if (!favoriteRepository.existsByUser_IdAndProduct_Id(userId, productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Favorite not found");
        }
        favoriteRepository.deleteByUser_IdAndProduct_Id(userId, productId);
    }

    @Transactional(readOnly = true)
    public Page<Product> getFavorites(Long userId, Pageable pageable) {
        return favoriteRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable)
                .map(Favorite::getProduct);
    }

    // Returns an empty set for an anonymous viewer so callers don't need
    // null-checks all over the mapping code.
    @Transactional(readOnly = true)
    public Set<Long> getFavoritedProductIds(Long userId, Collection<Long> productIds) {
        if (userId == null || productIds == null || productIds.isEmpty()) {
            return Set.of();
        }
        return favoriteRepository.findProductIdsByUserAndProductIds(userId, productIds)
                .stream().collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean isFavorited(Long userId, Long productId) {
        return userId != null && favoriteRepository.existsByUser_IdAndProduct_Id(userId, productId);
    }
}
