package com.olamide.UniSwap.Dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.olamide.UniSwap.Entity.Product;

import java.math.BigDecimal;
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
    @Size(max = 120, message = "Title must be at most 120 characters")
    private String title;

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    private String description;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 digits and 2 decimal places")
    private BigDecimal price;

    @NotBlank(message = "Category is required")
    @Size(max = 50, message = "Category must be at most 50 characters")
    private String category;

    @NotBlank(message = "Condition is required")
    @Size(max = 50, message = "Condition must be at most 50 characters")
    private String itemCondition;

    // Read-only from the client's perspective: the service never copies this
    // onto the entity on create/update, and on the way out it carries the
    // server-authoritative status as a string ("AVAILABLE"/"SOLD").
    private String status;

    private String imageUrl;

    private Long sellerId;

    private String sellerUsername;

    private LocalDateTime createdAt;

    // Whether the authenticated viewer has saved this listing. Always false
    // for anonymous requests and for the author's own read paths; computed by
    // the controller, never accepted from the client on create/update.
    private boolean favorited;

    public static ProductDTO fromEntity(Product product) {
        return fromEntity(product, false);
    }

    public static ProductDTO fromEntity(Product product, boolean favorited) {
        return ProductDTO.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory())
                .itemCondition(product.getItemCondition())
                .status(product.getStatus().name())
                .imageUrl(product.getImageUrl())
                .sellerId(product.getSeller().getId())
                .sellerUsername(product.getSeller().getUsername())
                .createdAt(product.getCreatedAt())
                .favorited(favorited)
                .build();
    }
}
