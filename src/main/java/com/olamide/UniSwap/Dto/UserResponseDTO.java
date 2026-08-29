package com.olamide.UniSwap.Dto;

import com.olamide.UniSwap.Entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Safe-to-expose shape of a User. Deliberately excludes the password hash.
// Never send the User entity itself back through a Controller — always map
// through this first.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {

    private Long id;
    private String username;
    private String email;
    private String phoneNumber;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String location;
    private boolean emailVerified;
    private boolean admin;
    private LocalDateTime createdAt;

    public static UserResponseDTO fromEntity(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .location(user.getLocation())
                .emailVerified(user.isEmailVerified())
                .admin(user.isAdmin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}