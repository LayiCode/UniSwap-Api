package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Dto.CreateReportRequest;
import com.olamide.UniSwap.Dto.UpdateReportStatusRequest;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.ProductStatus;
import com.olamide.UniSwap.Entity.Report;
import com.olamide.UniSwap.Entity.ReportStatus;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ProductService productService;
    private final UserService userService;

    @Transactional
    public Report create(Long reporterId, CreateReportRequest request) {
        Product product = productService.getById(request.getProductId());
        if (product.getSeller().getId().equals(reporterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot report your own listing");
        }
        if (reportRepository.existsByReporter_IdAndProduct_IdAndStatus(
                reporterId, product.getId(), ReportStatus.OPEN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already reported this listing");
        }

        User reporter = userService.getById(reporterId);
        String details = request.getDetails();
        return reportRepository.save(Report.builder()
                .reporter(reporter)
                .product(product)
                .reason(request.getReason())
                .details(details == null || details.isBlank() ? null : details.trim())
                .build());
    }

    // The moderation queue. A null status returns everything; the admin UI
    // usually filters to OPEN. Access control is enforced by hasRole("ADMIN")
    // in SecurityConfig.
    @Transactional(readOnly = true)
    public Page<Report> getReports(ReportStatus status, Pageable pageable) {
        return status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    // Resolve or dismiss a report. Resolving with removeProduct=true takes the
    // listing down (ProductStatus.REMOVED), which is what actually hides a
    // spam/scam listing from the marketplace.
    @Transactional
    public Report updateStatus(Long reportId, UpdateReportStatusRequest request) {
        Report report = getById(reportId);
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This report has already been decided");
        }

        if (request.getStatus() == ReportStatus.RESOLVED && Boolean.TRUE.equals(request.getRemoveProduct())) {
            productService.moderateStatus(report.getProduct().getId(), ProductStatus.REMOVED);
        }

        report.setStatus(request.getStatus());
        return reportRepository.save(report);
    }

    private Report getById(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }
}
