package com.olamide.UniSwap.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Returned from register/login. The client stores `token` and sends it back
// as "Authorization: Bearer <token>" on subsequent requests.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDTO {
    private String token;
    private UserResponseDTO user;
}