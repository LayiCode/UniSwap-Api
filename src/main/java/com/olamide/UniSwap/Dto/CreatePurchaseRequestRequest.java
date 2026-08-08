package com.olamide.UniSwap.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePurchaseRequestRequest {

    @NotNull(message = "Product id is required")
    private Long productId;

    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;
}
