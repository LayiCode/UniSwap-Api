package com.olamide.UniSwap.Dto;

import com.olamide.UniSwap.Entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
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
public class UpdateReportStatusRequest {

    @NotNull(message = "A status is required")
    private ReportStatus status;

    // Only meaningful when status is RESOLVED: true additionally removes the
    // listing from the marketplace (ProductStatus.REMOVED). Ignored otherwise.
    // Boolean, not the primitive — a missing field must deserialize as null
    // (Jackson 3 rejects a missing primitive).
    private Boolean removeProduct;
}
