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

    // New accounts must confirm their email with a code before they can log in.
    // OAuth users are verified implicitly by the identity provider.
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // No cascade: the Product side owns the FK and ProductService manages
    // listing lifecycle explicitly. A future user-delete endpoint must NOT
    // silently destroy a seller's listings via cascade.
    @OneToMany(mappedBy = "seller", fetch = FetchType.LAZY)
    private List<Product> products;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}