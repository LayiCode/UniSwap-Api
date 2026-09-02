package com.olamide.UniSwap.Dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Editable profile fields for PATCH /api/users/me. All fields are optional —
// only the provided ones are updated; nulls are ignored (a field cannot be
// cleared to empty via this DTO because null means "leave unchanged").
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    // Username is optional and NOT uniqueness-checked here (that only happens
    // at signup). Blank is treated by the service as "keep the current".
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    private String username;

    @Size(max = 20, message = "Phone number must be at most 20 characters")
    @Pattern(regexp = "^\\+?[0-9][0-9()\\-\\s]{6,17}[0-9]$",
            message = "Phone number must be a valid number")
    private String phoneNumber;

    @Size(max = 60, message = "Display name must be at most 60 characters")
    private String displayName;

    @Size(max = 500, message = "Bio must be at most 500 characters")
    private String bio;

    @Size(max = 120, message = "Location must be at most 120 characters")
    private String location;
}
