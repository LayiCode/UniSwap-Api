package com.olamide.UniSwap.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

// A buyer's intent to buy a listing. This is the first step of the purchase
// flow: the buyer makes a request, the seller sees it on their received
// list and accepts (marking the product SOLD) or declines. There is no
// payment integration — the request is a commitment/negotiation signal and
// the actual transaction happens off-platform, campus-style.
@Entity
@Table(name = "purchase_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Optional note from the buyer to the seller (e.g. "can meet at the
    // cafeteria on Friday").
    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Set once the request leaves PENDING; null until then.
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        if (this.status == null) {
            this.status = PurchaseRequestStatus.PENDING;
        }
    }
}
