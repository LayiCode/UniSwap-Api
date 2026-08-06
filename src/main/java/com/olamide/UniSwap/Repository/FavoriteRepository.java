package com.olamide.UniSwap.Repository;

import com.olamide.UniSwap.Entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByUser_IdAndProduct_Id(Long userId, Long productId);

    void deleteByUser_IdAndProduct_Id(Long userId, Long productId);

    // Favorites feed, most recently saved first. The entity graph fetches both
    // the product AND its seller so ProductDTO mapping never triggers a lazy
    // load (open-in-view is disabled).
    @EntityGraph(attributePaths = "product.seller")
    Page<Favorite> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Bulk lookup for decorating a whole browse page with the user's
    // favorited ids in one query instead of an exists() check per card.
    @Query("select f.product.id from Favorite f where f.user.id = :userId and f.product.id in :productIds")
    List<Long> findProductIdsByUserAndProductIds(
            @Param("userId") Long userId,
            @Param("productIds") Collection<Long> productIds);
}
