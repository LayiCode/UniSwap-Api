package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.UserResponseDTO;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Requires a valid JWT — SecurityConfig only leaves /api/auth/** and
    // GET /api/products/** open, so this falls under "anyRequest().authenticated()".
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getById(principal.getId());
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }

    // Public-ish profile lookup, e.g. viewing a seller's info from a listing.
    // Still requires auth under the current SecurityConfig rules — loosen
    // that in SecurityConfig if you want this browsable while logged out.
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        User user = userService.getById(id);
        return ResponseEntity.ok(UserResponseDTO.fromEntity(user));
    }
}