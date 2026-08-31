package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.PageResponseDTO;
import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Dto.PublicUserResponseDTO;
import com.olamide.UniSwap.Dto.UpdateProfileRequest;
import com.olamide.UniSwap.Dto.UserResponseDTO;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Service.ProductService;
import com.olamide.UniSwap.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserService userService;
    private final ProductService productService;

    // Requires a valid JWT — SecurityConfig only leaves /api/auth/** and
    // GET /api/products/** open, so this falls under "anyRequest().authenticated()".
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getById(requireId(principal));
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    // Partial update of the authenticated user's own profile (display name,
    // bio, location). All fields optional; nulls are left unchanged.
    @PatchMapping("/me")
    public ResponseEntity<UserResponseDTO> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.updateProfile(requireId(principal), request);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    // multipart/form-data — field name must be "file". Replaces the user's
    // avatar and returns the updated profile.
    @PostMapping("/me/avatar")
    public ResponseEntity<UserResponseDTO> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.uploadAvatar(requireId(principal), file);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    // Public profile lookup (e.g. viewing a seller's profile from a listing).
    // Returns the minimal PublicUserResponseDTO — email and phone number are
    // withheld so buyers can't scrape every seller's contact details.
    @GetMapping("/{id}")
    public ResponseEntity<PublicUserResponseDTO> getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        PublicUserResponseDTO dto = PublicUserResponseDTO.fromEntity(user);
        dto.setActiveListingsCount(userService.countActiveListings(id));
        return ResponseEntity.ok(dto);
    }

    // The public profile's currently-available listings for this seller.
    @GetMapping("/{id}/products")
    public ResponseEntity<PageResponseDTO<ProductDTO>> getUserProducts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        userService.getById(id); // 404 if the user doesn't exist
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Product> products = productService.getAvailableBySeller(id, PageRequest.of(safePage, safeSize));
        return ResponseEntity.ok(PageResponseDTO.from(products, products.getContent().stream()
                .map(p -> ProductDTO.fromEntity(p, false))
                .toList()));
    }

    // Soft-deletes the authenticated user's own account: anonymizes the email/
    // username, hides the profile and any listings, and prevents further login.
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@AuthenticationPrincipal UserPrincipal principal) {
        userService.deactivate(requireId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long requireId(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getId();
    }
}
