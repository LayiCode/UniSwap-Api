package com.olamide.UniSwap.Dto;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class RegisterRequest {

    // Username is optional at signup — a placeholder is derived and the user
    // can pick a real one later. Only enforce length if a value is provided.
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    // Uncomment to restrict signups to LAUTECH student emails only:
    // @Pattern(regexp = "^[A-Za-z0-9._%+-]+@student\\.lautech\\.edu\\.ng$",
    //         message = "Email must be a valid LAUTECH student email")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "Password must contain at least one letter and one number")
    private String password;

    // Phone number: relaxed on purpose — Nigerians commonly write them with a
    // leading +234, national 0, spaces, dashes or brackets. We strip non-digits
    // and require a plausible 10-14 digit core rather than rejecting a valid
    // address with a strict pattern.
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9][0-9()\\-\\s]{6,17}[0-9]$",
            message = "Phone number must be a valid number")
    private String phoneNumber;
}