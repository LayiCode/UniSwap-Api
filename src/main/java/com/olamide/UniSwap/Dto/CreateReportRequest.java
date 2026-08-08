package com.olamide.UniSwap.Dto;

import com.olamide.UniSwap.Entity.ReportReason;
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
public class CreateReportRequest {

    @NotNull(message = "Product id is required")
    private Long productId;

    @NotNull(message = "A reason is required")
    private ReportReason reason;

    @Size(max = 2000, message = "Details must be at most 2000 characters")
    private String details;
}
