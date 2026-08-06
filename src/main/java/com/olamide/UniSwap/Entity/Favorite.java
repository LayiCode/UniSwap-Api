package com.olamide.UniSwap.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "favorites",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // The unique constraint on (user_id, product_id) is the real primary key —
    // a user can favorite a given listing at most once. The surrogate id just
    // keeps the JPA mapping straightforward; a second favorite attempt is
    // rejected with a CONFLICT before it ever hits the database.
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
