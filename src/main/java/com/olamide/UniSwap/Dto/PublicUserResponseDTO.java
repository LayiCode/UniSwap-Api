package com.olamide.UniSwap.Dto;

import com.olamide.UniSwap.Entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// The minimal, safe shape of another user's profile shown on a listing or
// product page. Deliberately EXCLUDES email and phoneNumber — a buyer should
// not be able to harvest every seller's contact details by id. The full
// profile (with contact info) is only returned to the user themselves via
// GET /api/users/me.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicUserResponseDTO {

    private Long id;
    private String username;
    private LocalDateTime createdAt;

    public static PublicUserResponseDTO fromEntity(User user) {
        return PublicUserResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
