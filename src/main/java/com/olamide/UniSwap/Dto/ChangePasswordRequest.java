package com.olamide.UniSwap.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Request body for PATCH /api/users/me/password: changing the password of the
// currently authenticated account. The current password must be provided and
// correct — this proves the caller knows the account secret, so an account
// with a leaked token can't silently swap credentials.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 64, message = "New password must be between 8 and 64 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "New password must contain at least one letter and one number")
    private String newPassword;
}
