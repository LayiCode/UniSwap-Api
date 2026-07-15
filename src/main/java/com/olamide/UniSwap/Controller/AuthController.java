package com.olamide.UniSwap.Controller;

import com.olamide.UniSwap.Dto.AuthResponseDTO;
import com.olamide.UniSwap.Dto.LoginRequest;
import com.olamide.UniSwap.Dto.RegisterRequest;
import com.olamide.UniSwap.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Publicly accessible endpoints — see SecurityConfig, /api/auth/** is permitAll.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponseDTO response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequest request) {
        AuthResponseDTO response = userService.login(request);
        return ResponseEntity.ok(response);
    }
}