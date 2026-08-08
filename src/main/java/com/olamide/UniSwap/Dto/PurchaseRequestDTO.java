package com.olamide.UniSwap.Dto;

import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.PurchaseRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Read-only snapshot of a purchase request plus the parts of the listing and
// the two users that the UI needs. Never constructed by the client — the
// server builds it from the entity so the fields can't be forged.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestDTO {

    private Long id;

    private Long productId;
    private String productTitle;
    private String productImageUrl;
    private BigDecimal productPrice;
    private String productStatus;

    private Long sellerId;
    private String sellerUsername;

    private Long buyerId;
    private String buyerUsername;

    private String message;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;

    public static PurchaseRequestDTO fromEntity(PurchaseRequest request) {
        Product product = request.getProduct();
        return PurchaseRequestDTO.builder()
                .id(request.getId())
                .productId(product.getId())
                .productTitle(product.getTitle())
                .productImageUrl(product.getImageUrl())
                .productPrice(product.getPrice())
                .productStatus(product.getStatus().name())
                .sellerId(product.getSeller().getId())
                .sellerUsername(product.getSeller().getUsername())
                .buyerId(request.getBuyer().getId())
                .buyerUsername(request.getBuyer().getUsername())
                .message(request.getMessage())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt())
                .decidedAt(request.getDecidedAt())
                .build();
    }
}
