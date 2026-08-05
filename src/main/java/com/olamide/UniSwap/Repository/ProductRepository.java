package com.olamide.UniSwap.Repository;

import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // @EntityGraph eagerly fetches the seller in the SAME query, killing the
    // N+1 that previously fired one "select users" per product when the DTO
    // mapped product.getSeller() outside a transaction.
    @EntityGraph(attributePaths = "seller")
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "seller")
    Page<Product> findByCategoryAndStatus(String category, ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "seller")
    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    @EntityGraph(attributePaths = "seller")
    Page<Product> findByTitleContainingIgnoreCaseAndStatus(String keyword, ProductStatus status, Pageable pageable);

    // Join-fetch the seller for single lookups too, so ProductDTO mapping in
    // the controller never triggers a lazy load (which would throw once
    // open-in-view is disabled).
    @Query("select p from Product p join fetch p.seller where p.id = :id")
    Optional<Product> findByIdWithSeller(@Param("id") Long id);
}
