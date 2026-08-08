package com.olamide.UniSwap.Repository;

import com.olamide.UniSwap.Entity.PurchaseRequest;
import com.olamide.UniSwap.Entity.PurchaseRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    // One buyer can only have a single open request on a given listing; the
    // service checks this before writing so a duplicate PENDING is rejected
    // with a CONFLICT instead of silently piling up.
    boolean existsByBuyer_IdAndProduct_IdAndStatus(
            Long buyerId, Long productId, PurchaseRequestStatus status);

    // Received: requests on listings the seller owns, newest first.
    @EntityGraph(attributePaths = {"product.seller", "buyer"})
    Page<PurchaseRequest> findByProduct_Seller_IdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    // Sent: requests the buyer has made, newest first.
    @EntityGraph(attributePaths = {"product.seller", "buyer"})
    Page<PurchaseRequest> findByBuyer_IdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);

    // All OPEN requests on a product — used when one request is accepted so
    // every other waiting buyer can be told the listing is gone.
    List<PurchaseRequest> findByProduct_IdAndStatus(Long productId, PurchaseRequestStatus status);

    // Accepting a request maps the product/seller/buyer to a DTO immediately
    // afterwards, so the detail graph (product + seller + buyer) is fetched
    // eagerly instead of tripping open-in-view's lazy-load guard.
    @Override
    @EntityGraph(attributePaths = {"product.seller", "buyer"})
    Optional<PurchaseRequest> findById(Long id);
}
