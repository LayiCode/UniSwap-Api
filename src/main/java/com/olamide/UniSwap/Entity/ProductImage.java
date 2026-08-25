package com.olamide.UniSwap.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Public URL of the stored image (Supabase Storage or local disk).
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    // Zero-based display position; index 0 is the cover and always mirrors
    // products.image_url so legacy readers keep working.
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
