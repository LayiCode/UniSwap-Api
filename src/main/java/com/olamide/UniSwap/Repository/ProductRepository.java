package com.olamide.UniSwap.Repository;

import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Single query for the whole browse feed: every filter is optional and
    // combined with AND, so search/category/condition/price can be mixed
    // freely (previously search and category were mutually exclusive). The
    // keyword parameter arrives already wrapped in %..% and lowercased by the
    // service, and it matches BOTH title and description.
    @EntityGraph(attributePaths = "seller")
    @Query("select p from Product p where p.status = :status " +
            "and (:keyword is null or lower(p.title) like :keyword or lower(p.description) like :keyword) " +
            "and (:category is null or p.category = :category) " +
            "and (:condition is null or p.itemCondition = :condition) " +
            "and (:minPrice is null or p.price >= :minPrice) " +
            "and (:maxPrice is null or p.price <= :maxPrice)")
    Page<Product> searchFilters(
            @Param("status") ProductStatus status,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("condition") String condition,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    @EntityGraph(attributePaths = "seller")
    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    @EntityGraph(attributePaths = "seller")
    Page<Product> findBySellerIdAndStatus(Long sellerId, ProductStatus status, Pageable pageable);

    // Count of a user's listings in a given lifecycle state. Used for the
    // "active listings" count on a public profile.
    @Query("select count(p) from Product p where p.seller.id = :sellerId and p.status = :status")
    long countBySellerIdAndStatus(@Param("sellerId") Long sellerId, @Param("status") ProductStatus status);

    // Join-fetch seller AND photos for single lookups, so ProductDTO mapping
    // in the controller never triggers a lazy load (which would throw once
    // open-in-view is disabled). @OrderBy on Product.images sorts them.
    @Query("select p from Product p join fetch p.seller left join fetch p.images where p.id = :id")
    Optional<Product> findByIdWithSeller(@Param("id") Long id);
}
