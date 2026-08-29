package com.olamide.UniSwap.Entity;


import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Optimistic lock. On every UPDATE Hibernate bumps this column; a
    // concurrent write that is based on a stale row fails with
    // ObjectOptimisticLockingFailureException instead of silently
    // overwriting the other transaction's change (e.g. two "mark sold"
    // requests racing each other).
    @Version
    private Long version;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // DECIMAL, not DOUBLE: binary floats can't represent money exactly
    // (0.1 + 0.2 != 0.3), so prices use fixed-point arithmetic.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 50)
    private String category; // e.g., "Electronics", "Books", "Furniture"

    @Column(nullable = false, length = 50)
    private String itemCondition; // e.g., "Brand New", "Neatly Used"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status; // "AVAILABLE" or "SOLD"

    // Free-text pickup/meetup location for this listing, e.g. "North Gate,
    // LAUTECH". Optional and unstructured for now.
    @Column(name = "location", length = 120)
    private String location;

    @Column(name = "image_url")
    private String imageUrl;

    // All photos of the listing, display order. imageUrl above always mirrors
    // images[0] (the cover) so cards, reports and purchase requests — which
    // only read the plain column — never need the collection. Rows are
    // managed exclusively by ProductService.uploadImages(); update() must
    // not touch either field.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        // Store wall-clock time in UTC so timestamps don't drift with the
        // JVM's local zone; the JDBC connection also negotiates UTC.
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        if (this.status == null) {
            this.status = ProductStatus.AVAILABLE; // Default status when created
        }
    }
}
