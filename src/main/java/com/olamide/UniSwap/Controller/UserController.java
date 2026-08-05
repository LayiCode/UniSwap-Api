package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.PublicUserResponseDTO;
import com.olamide.UniSwap.Dto.UserResponseDTO;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Requires a valid JWT — SecurityConfig only leaves /api/auth/** and
    // GET /api/products/** open, so this falls under "anyRequest().authenticated()".
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userService.getById(principal.getId());
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    // Public profile lookup (e.g. viewing a seller's name from a listing).
    // Returns the minimal PublicUserResponseDTO — email and phone number are
    // withheld so buyers can't scrape every seller's contact details.
    @GetMapping("/{id}")
    public ResponseEntity<PublicUserResponseDTO> getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        return ResponseEntity.ok(PublicUserResponseDTO.fromEntity(user));
    }
}