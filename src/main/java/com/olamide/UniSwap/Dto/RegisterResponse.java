package com.olamide.UniSwap.Dto;

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
public class RegisterResponse {

    private UserResponseDTO user;

    private String message;

    // Present only when the verification email could not be delivered, so the
    // user can still activate their account. Omitted entirely when email is
    // working — never include the code in a normal successful send.
    private String verificationCode;
}
