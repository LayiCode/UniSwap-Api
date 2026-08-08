package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Dto.CreatePurchaseRequestRequest;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.ProductStatus;
import com.olamide.UniSwap.Entity.PurchaseRequest;
import com.olamide.UniSwap.Entity.PurchaseRequestStatus;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.PurchaseRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseRequestService {

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final ProductService productService;
    private final UserService userService;

    @Transactional
    public PurchaseRequest create(Long buyerId, CreatePurchaseRequestRequest request) {
        Product product = productService.getById(request.getProductId());
        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This listing is no longer available for purchase");
        }
        if (product.getSeller().getId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot request to buy your own listing");
        }
        if (hasPendingRequest(buyerId, product.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have a pending request for this listing");
        }

        User buyer = userService.getById(buyerId);
        String message = request.getMessage();
        return purchaseRequestRepository.save(PurchaseRequest.builder()
                .buyer(buyer)
                .product(product)
                .message(message == null || message.isBlank() ? null : message.trim())
                .build());
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequest> getReceived(Long sellerId, Pageable pageable) {
        return purchaseRequestRepository.findByProduct_Seller_IdOrderByCreatedAtDesc(sellerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseRequest> getSent(Long buyerId, Pageable pageable) {
        return purchaseRequestRepository.findByBuyer_IdOrderByCreatedAtDesc(buyerId, pageable);
    }

    // Seller says yes: the listing is marked SOLD (through ProductService so
    // its ownership/optimistic-lock rules apply) and every other pending
    // request on the same product is auto-declined — it's gone, and those
    // buyers need to know instead of waiting forever.
    @Transactional
    public PurchaseRequest accept(Long purchaseRequestId, Long sellerId) {
        PurchaseRequest purchaseRequest = getDecidable(purchaseRequestId, sellerId);
        Product product = purchaseRequest.getProduct();

        productService.markAsSold(product.getId(), sellerId);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<PurchaseRequest> otherPending = purchaseRequestRepository
                .findByProduct_IdAndStatus(product.getId(), PurchaseRequestStatus.PENDING);
        for (PurchaseRequest other : otherPending) {
            if (!other.getId().equals(purchaseRequestId)) {
                other.setStatus(PurchaseRequestStatus.DECLINED);
                other.setDecidedAt(now);
            }
        }

        purchaseRequest.setStatus(PurchaseRequestStatus.ACCEPTED);
        purchaseRequest.setDecidedAt(now);
        return purchaseRequestRepository.save(purchaseRequest);
    }

    @Transactional
    public PurchaseRequest decline(Long purchaseRequestId, Long sellerId) {
        PurchaseRequest purchaseRequest = getDecidable(purchaseRequestId, sellerId);
        purchaseRequest.setStatus(PurchaseRequestStatus.DECLINED);
        purchaseRequest.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        return purchaseRequestRepository.save(purchaseRequest);
    }

    @Transactional
    public PurchaseRequest cancel(Long purchaseRequestId, Long buyerId) {
        PurchaseRequest purchaseRequest = getById(purchaseRequestId);
        if (!purchaseRequest.getBuyer().getId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot cancel this request");
        }
        requirePending(purchaseRequest);
        purchaseRequest.setStatus(PurchaseRequestStatus.CANCELLED);
        purchaseRequest.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        return purchaseRequestRepository.save(purchaseRequest);
    }

    // Used by the product detail endpoint so the "Request to buy" button can
    // flip to "Request sent". Returns false for anonymous viewers.
    @Transactional(readOnly = true)
    public boolean hasPendingRequest(Long userId, Long productId) {
        return userId != null && purchaseRequestRepository
                .existsByBuyer_IdAndProduct_IdAndStatus(userId, productId, PurchaseRequestStatus.PENDING);
    }

    // Shared guard for the seller-only decisions: loads the request, verifies
    // it is still open, and verifies the caller is the product's seller.
    private PurchaseRequest getDecidable(Long purchaseRequestId, Long sellerId) {
        PurchaseRequest purchaseRequest = getById(purchaseRequestId);
        requirePending(purchaseRequest);
        if (!purchaseRequest.getProduct().getSeller().getId().equals(sellerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this listing");
        }
        return purchaseRequest;
    }

    private PurchaseRequest getById(Long purchaseRequestId) {
        return purchaseRequestRepository.findById(purchaseRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase request not found"));
    }

    private void requirePending(PurchaseRequest purchaseRequest) {
        if (purchaseRequest.getStatus() != PurchaseRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been decided");
        }
    }
}
