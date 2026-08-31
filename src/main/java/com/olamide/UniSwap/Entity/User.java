package com.olamide.UniSwap.Entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email; // You can validate this to ensure it ends with @student.lautech.edu.ng later

    @Column(nullable = false)
    private String password;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    // Human-friendly name shown on listings, chat, and profiles. Falls back to
    // username when unset.
    @Column(name = "display_name")
    private String displayName;

    // Public avatar URL (Supabase storage). Never accepted from the client by
    // value — avatar uploads go through the POST /users/me/avatar endpoint.
    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(length = 500)
    private String bio;

    // Free-text default location used to prefill new listings and shown on a
    // user's public profile.
    @Column(length = 120)
    private String location;

    // Soft-delete marker: null means the account is active. When an account is
    // deleted we keep the row (for FK integrity with chat/purchase/report
    // history) but set this timestamp, anonymize the email/username, and hide
    // the profile + listings. Non-null blocks login and public exposure.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // New accounts must confirm their email with a code before they can log in.
    // OAuth users are verified implicitly by the identity provider.
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // Staff/admin flag. Normal users never set this (register/OAuth builders
    // leave it false); it is flipped by a privileged action or direct DB
    // change. UserPrincipal turns it into a ROLE_ADMIN authority so the
    // moderation endpoints can be secured with hasRole("ADMIN").
    @Column(nullable = false)
    private boolean admin;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // No cascade: the Product side owns the FK and ProductService manages
    // listing lifecycle explicitly. A future user-delete endpoint must NOT
    // silently destroy a seller's listings via cascade.
    @OneToMany(mappedBy = "seller", fetch = FetchType.LAZY)
    private List<Product> products;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}