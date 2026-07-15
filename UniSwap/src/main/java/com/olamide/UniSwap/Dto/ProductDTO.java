package com.olamide.UniSwap.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.olamide.UniSwap.Entity.Product;

import java.time.LocalDateTime;

// Used both to receive a new/updated listing from the client and to send
// listing data back out. id, status, sellerId, sellerUsername, and createdAt
// are ignored on the way in and populated by the server on the way out.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDTO {

    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private Double price;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Condition is required")
    private String itemCondition;

    private String status;

    private String imageUrl;

    private Long sellerId;

    private String sellerUsername;

    private LocalDateTime createdAt;

    public static ProductDTO fromEntity(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .itemCondition(product.getItemCondition())
                .status(product.getStatus())
                .imageUrl(product.getImageUrl())
                .sellerId(product.getSeller().getId())
                .sellerUsername(product.getSeller().getUsername())
                .createdAt(product.getCreatedAt())
                .build();
    }
}