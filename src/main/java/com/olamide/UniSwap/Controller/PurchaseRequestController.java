package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.CreatePurchaseRequestRequest;
import com.olamide.UniSwap.Dto.PageResponseDTO;
import com.olamide.UniSwap.Dto.PurchaseRequestDTO;
import com.olamide.UniSwap.Entity.PurchaseRequest;
import com.olamide.UniSwap.Service.PurchaseRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Every endpoint here is behind .anyRequest().authenticated(): requests are
// private to the two parties (buyer and listing owner). There is no payment
// integration — accepting a request marks the listing SOLD and the exchange
// happens between the students.
@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PurchaseRequestService purchaseRequestService;

    // POST /api/purchases -> place a request to buy a listing
    @PostMapping
    public ResponseEntity<PurchaseRequestDTO> create(
            @Valid @RequestBody CreatePurchaseRequestRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PurchaseRequest created = purchaseRequestService.create(currentUserId(principal), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PurchaseRequestDTO.fromEntity(created));
    }

    // GET /api/purchases?scope=received|sent&page=&size=
    //   scope=received (default) -> requests on MY listings (I'm the seller)
    //   scope=sent              -> requests I have placed (I'm the buyer)
    @GetMapping
    public ResponseEntity<PageResponseDTO<PurchaseRequestDTO>> list(
            @RequestParam(defaultValue = "received") String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Pageable pageable = buildPageable(page, size);
        Long userId = currentUserId(principal);
        Page<PurchaseRequest> requests = switch (scope) {
            case "received" -> purchaseRequestService.getReceived(userId, pageable);
            case "sent" -> purchaseRequestService.getSent(userId, pageable);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown scope '" + scope + "' (expected received or sent)");
        };
        return ResponseEntity.ok(PageResponseDTO.from(requests, requests.getContent().stream()
                .map(PurchaseRequestDTO::fromEntity)
                .toList()));
    }

    // Seller accepts -> listing becomes SOLD, other pending requests declined.
    @PostMapping("/{id}/accept")
    public ResponseEntity<PurchaseRequestDTO> accept(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PurchaseRequest accepted = purchaseRequestService.accept(id, currentUserId(principal));
        return ResponseEntity.ok(PurchaseRequestDTO.fromEntity(accepted));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<PurchaseRequestDTO> decline(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PurchaseRequest declined = purchaseRequestService.decline(id, currentUserId(principal));
        return ResponseEntity.ok(PurchaseRequestDTO.fromEntity(declined));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PurchaseRequestDTO> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PurchaseRequest cancelled = purchaseRequestService.cancel(id, currentUserId(principal));
        return ResponseEntity.ok(PurchaseRequestDTO.fromEntity(cancelled));
    }

    private Long currentUserId(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }

    private Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
