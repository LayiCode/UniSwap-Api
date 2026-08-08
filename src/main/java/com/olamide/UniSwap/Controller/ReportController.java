package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.CreateReportRequest;
import com.olamide.UniSwap.Dto.PageResponseDTO;
import com.olamide.UniSwap.Dto.ReportDTO;
import com.olamide.UniSwap.Dto.UpdateReportStatusRequest;
import com.olamide.UniSwap.Entity.Report;
import com.olamide.UniSwap.Entity.ReportStatus;
import com.olamide.UniSwap.Service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// POST /api/reports is open to any authenticated student. Everything else
// (the queue + deciding reports) is ADMIN-only — enforced by hasRole("ADMIN")
// in SecurityConfig, and these endpoints simply assume that has already run.
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportDTO> create(
            @Valid @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Report created = reportService.create(currentUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportDTO.fromEntity(created));
    }

    // GET /api/reports?status=OPEN|RESOLVED|DISMISSED&page=&size= (ADMIN)
    // A missing status returns every report, newest first.
    @GetMapping
    public ResponseEntity<PageResponseDTO<ReportDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        ReportStatus filter = parseStatus(status);
        Pageable pageable = buildPageable(page, size);
        Page<Report> reports = reportService.getReports(filter, pageable);
        return ResponseEntity.ok(PageResponseDTO.from(reports, reports.getContent().stream()
                .map(ReportDTO::fromEntity)
                .toList()));
    }

    // PATCH /api/reports/{id}  body: { "status": "RESOLVED"|"DISMISSED",
    // "removeProduct": true|false } (ADMIN)
    @PatchMapping("/{id}")
    public ResponseEntity<ReportDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReportStatusRequest request
    ) {
        Report updated = reportService.updateStatus(id, request);
        return ResponseEntity.ok(ReportDTO.fromEntity(updated));
    }

    private Long currentUserId(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }

    private ReportStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown status '" + status + "' (expected OPEN, RESOLVED or DISMISSED)");
        }
    }

    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
