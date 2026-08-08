package com.olamide.UniSwap.Repository;

import com.olamide.UniSwap.Entity.Report;
import com.olamide.UniSwap.Entity.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Report queue used by the moderation view. Every query that returns a Report
// fetches the product (with its seller) and the reporter eagerly so the DTO
// mapping in the controller never triggers a lazy load (open-in-view is off).
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporter_IdAndProduct_IdAndStatus(Long reporterId, Long productId, ReportStatus status);

    @EntityGraph(attributePaths = {"product.seller", "reporter"})
    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"product.seller", "reporter"})
    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    // updateStatus() loads a single report, mutates it, and hands it back for
    // DTO mapping after the transaction closes — without the graph the lazy
    // reporter/product proxies can't be read then.
    @Override
    @EntityGraph(attributePaths = {"product.seller", "reporter"})
    Optional<Report> findById(Long id);
}
