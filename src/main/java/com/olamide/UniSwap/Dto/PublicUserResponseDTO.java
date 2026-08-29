package com.olamide.UniSwap.Dto;

import com.olamide.UniSwap.Entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// The minimal, safe shape of another user's profile shown on a listing,
// product page or profile page. Deliberately EXCLUDES email and phoneNumber —
// a buyer should not be able to harvest every seller's contact details by id.
// The full profile (with contact info) is only returned to the user themselves
// via GET /api/users/me.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicUserResponseDTO {

    private Long id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String location;
    private LocalDateTime createdAt;
    private long activeListingsCount;

    public static PublicUserResponseDTO fromEntity(User user) {
        return PublicUserResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .location(user.getLocation())
                .createdAt(user.getCreatedAt())
                .activeListingsCount(0)
                .build();
    }
}
