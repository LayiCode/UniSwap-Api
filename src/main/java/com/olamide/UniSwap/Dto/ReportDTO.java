package com.olamide.UniSwap.Dto;

import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.Report;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Read-only snapshot of a report plus the reported listing and reporter
// context the moderation view needs. Server-built only, never accepted from
// the client.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportDTO {

    private Long id;

    private Long productId;
    private String productTitle;
    private String productImageUrl;
    private BigDecimal productPrice;
    private String productStatus;
    private Long sellerId;
    private String sellerUsername;

    private Long reporterId;
    private String reporterUsername;

    private String reason;
    private String details;
    private String status;
    private LocalDateTime createdAt;

    public static ReportDTO fromEntity(Report report) {
        Product product = report.getProduct();
        return ReportDTO.builder()
                .id(report.getId())
                .productId(product.getId())
                .productTitle(product.getTitle())
                .productImageUrl(product.getImageUrl())
                .productPrice(product.getPrice())
                .productStatus(product.getStatus().name())
                .sellerId(product.getSeller().getId())
                .sellerUsername(product.getSeller().getUsername())
                .reporterId(report.getReporter().getId())
                .reporterUsername(report.getReporter().getUsername())
                .reason(report.getReason().name())
                .details(report.getDetails())
                .status(report.getStatus().name())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
