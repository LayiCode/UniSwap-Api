package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Dto.AuthResponseDTO;
import com.olamide.UniSwap.Dto.RegisterRequest;
import com.olamide.UniSwap.Dto.RegisterResponse;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Pure unit tests — UserRepository, PasswordEncoder, JwtService, and
// EmailVerificationService are all mocked, so these run in milliseconds with
// no database or Spring context involved. They verify UserService's own logic
// in isolation.
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .username("olamide")
                .email("olamide@student.lautech.edu.ng")
                .password("password123")
                .phoneNumber("08012345678")
                .build();
    }

    @Test
    void register_createsUnverifiedUserAndEmailsCode_whenEmailAndUsernameAreFree() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(emailVerificationService.generateAndSendCode(anyString(), any()))
                .thenReturn(new CodeDelivery("123456", true));

        RegisterResponse response = userService.register(registerRequest);

        assertThat(response.getUser().getUsername()).isEqualTo("olamide");
        assertThat(response.getUser().getEmail()).isEqualTo("olamide@student.lautech.edu.ng");
        assertThat(response.getUser().isEmailVerified()).isFalse();
        assertThat(response.getMessage()).isNotBlank();
        verify(userRepository).save(any(User.class));
        // The stored password must be the hash, never the raw input.
        verify(passwordEncoder).encode("password123");
        // A signup code must be emailed so the account can be unlocked.
        verify(emailVerificationService).generateAndSendCode(
                eq("olamide@student.lautech.edu.ng"), eq(VerificationPurpose.SIGNUP));
    }

    @Test
    void register_throwsConflict_whenEmailAlreadyRegistered() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsConflict_whenUsernameAlreadyTaken() {
        when(userRepository.existsByUsername(anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Username is already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_throwsBadRequest_whenPasswordIsSimilarToUsername() {
        RegisterRequest similar = RegisterRequest.builder()
                .username("johnsmith")
                .email("similar@example.com")
                .password("johnsmith123") // contains the username
                .phoneNumber("08012345678")
                .build();

        assertThatThrownBy(() -> userService.register(similar))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Password must not be the same as or too similar to your username");

        verify(userRepository, never()).save(any());
    }

    @Test
    void normalizePhone_stripsSeparatorsAndLeadingPlus() {
        assertThat(UserService.normalizePhone("+234 801 234 5678")).isEqualTo("2348012345678");
        assertThat(UserService.normalizePhone("+234-801-234-5678")).isEqualTo("2348012345678");
        assertThat(UserService.normalizePhone("+2348012345678")).isEqualTo("2348012345678");
        assertThat(UserService.normalizePhone("08012345678")).isEqualTo("08012345678");
        assertThat(UserService.normalizePhone("+234 (801) 234 5678")).isEqualTo("2348012345678");
        assertThat(UserService.normalizePhone("  +2348012345678  ")).isEqualTo("2348012345678");
    }

    @Test
    void register_storesNormalizedInternationalPhone() {
        registerRequest.setPhoneNumber("+234 801 234 5678");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailVerificationService.generateAndSendCode(anyString(), any()))
                .thenReturn(new CodeDelivery("123456", true));

        RegisterResponse response = userService.register(registerRequest);

        assertThat(response.getUser().getPhoneNumber()).isEqualTo("2348012345678");
    }

    @Test
    void verifyEmail_marksAccountVerified_whenCodeValid() {
        User user = User.builder()
                .id(1L)
                .username("olamide")
                .email("olamide@student.lautech.edu.ng")
                .password("hashed-password")
                .phoneNumber("08012345678")
                .build();

        when(userRepository.findByEmail("olamide@student.lautech.edu.ng")).thenReturn(Optional.of(user));
        when(emailVerificationService.verifyCode(
                "olamide@student.lautech.edu.ng", VerificationPurpose.SIGNUP, "123456")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User verified = userService.verifyEmail("olamide@student.lautech.edu.ng", "123456");

        assertThat(verified.isEmailVerified()).isTrue();
    }

    @Test
    void verifyEmail_throwsBadRequest_whenCodeInvalid() {
        when(userRepository.findByEmail("olamide@student.lautech.edu.ng"))
                .thenReturn(Optional.of(User.builder().email("olamide@student.lautech.edu.ng").build()));
        when(emailVerificationService.verifyCode(
                "olamide@student.lautech.edu.ng", VerificationPurpose.SIGNUP, "000000")).thenReturn(false);

        assertThatThrownBy(() -> userService.verifyEmail("olamide@student.lautech.edu.ng", "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid or expired verification code");
    }

    @Test
    void resendSignupCode_sendsCode_whenAccountExistsAndIsUnverified() {
        User user = User.builder().email("unverified@example.com").build();
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));

        userService.resendSignupCode("unverified@example.com");

        verify(emailVerificationService).generateAndSendCode(
                "unverified@example.com", VerificationPurpose.SIGNUP);
    }

    @Test
    void resendSignupCode_sendsNothing_forUnknownOrVerifiedEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        userService.resendSignupCode("nobody@example.com");

        User verified = User.builder().email("verified@example.com").emailVerified(true).build();
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(verified));
        userService.resendSignupCode("verified@example.com");

        verify(emailVerificationService, never()).generateAndSendCode(anyString(), any());
    }

    @Test
    void requestLoginCode_sendsCode_whenAccountExistsAndIsVerified() {
        User user = User.builder()
                .email("olamide@student.lautech.edu.ng")
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("olamide@student.lautech.edu.ng")).thenReturn(Optional.of(user));

        userService.requestLoginCode("olamide@student.lautech.edu.ng");

        verify(emailVerificationService).generateAndSendCode(
                "olamide@student.lautech.edu.ng", VerificationPurpose.LOGIN);
    }

    @Test
    void requestLoginCode_sendsNothing_forUnknownEmail() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        userService.requestLoginCode("nobody@example.com");

        verify(emailVerificationService, never()).generateAndSendCode(anyString(), any());
    }

    @Test
    void requestLoginCode_sendsNothing_forUnverifiedEmail() {
        User user = User.builder().email("unverified@example.com").build();
        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));

        userService.requestLoginCode("unverified@example.com");

        verify(emailVerificationService, never()).generateAndSendCode(anyString(), any());
    }

    @Test
    void loginWithCode_returnsToken_whenAccountVerifiedAndCodeValid() {
        User user = User.builder()
                .id(1L)
                .username("olamide")
                .email("olamide@student.lautech.edu.ng")
                .password("hashed-password")
                .phoneNumber("08012345678")
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail("olamide@student.lautech.edu.ng")).thenReturn(Optional.of(user));
        when(emailVerificationService.verifyCode(
                "olamide@student.lautech.edu.ng", VerificationPurpose.LOGIN, "123456")).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("fake-jwt-token");

        AuthResponseDTO response = userService.loginWithCode("olamide@student.lautech.edu.ng", "123456");

        assertThat(response.getToken()).isEqualTo("fake-jwt-token");
        assertThat(response.getUser().getEmail()).isEqualTo("olamide@student.lautech.edu.ng");
    }

    @Test
    void loginWithCode_throwsUnauthorized_whenAccountNotVerified() {
        User user = User.builder().email("olamide@student.lautech.edu.ng").build();
        when(userRepository.findByEmail("olamide@student.lautech.edu.ng")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.loginWithCode("olamide@student.lautech.edu.ng", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Email not verified");

        verify(emailVerificationService, never()).verifyCode(anyString(), any(), anyString());
    }

    @Test
    void loginWithCode_throwsUnauthorized_whenCodeIsWrong() {
        User user = User.builder()
                .email("olamide@student.lautech.edu.ng")
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("olamide@student.lautech.edu.ng")).thenReturn(Optional.of(user));
        when(emailVerificationService.verifyCode(
                "olamide@student.lautech.edu.ng", VerificationPurpose.LOGIN, "000000")).thenReturn(false);

        assertThatThrownBy(() -> userService.loginWithCode("olamide@student.lautech.edu.ng", "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid email or code");
    }

    @Test
    void loginWithCode_throwsUnauthorized_whenEmailDoesNotExist() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loginWithCode("nobody@example.com", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid email or code");
    }
}
