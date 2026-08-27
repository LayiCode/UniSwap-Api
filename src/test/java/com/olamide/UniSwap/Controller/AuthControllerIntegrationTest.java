package com.olamide.UniSwap.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olamide.UniSwap.Dto.RegisterRequest;
import com.olamide.UniSwap.Dto.ResetPasswordRequest;
import com.olamide.UniSwap.Entity.EmailVerificationCode;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.EmailVerificationCodeRepository;
import com.olamide.UniSwap.Repository.UserRepository;
import com.olamide.UniSwap.Service.EmailVerificationService;
import com.olamide.UniSwap.Service.VerificationPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Full-stack test: real Spring context, real SecurityConfig, real
// GlobalExceptionHandler, running against an in-memory H2 database
// (see application-test.yml) instead of MySQL. @Transactional rolls each
// test's data back so test order never matters.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationCodeRepository codeRepository;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Plain instantiation rather than @Autowired: Spring Boot 4 configures a
    // Jackson 3 ObjectMapper, and this class only needs a mapper to serialize
    // request bodies, so a default Jackson 2 mapper is enough.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RegisterRequest validRegisterRequest() {
        return RegisterRequest.builder()
                .username("olamide")
                .email("olamide@student.lautech.edu.ng")
                .password("password123")
                .phoneNumber("08012345678")
                .build();
    }

    // Mints a signup code directly through the service (the raw code is only
    // ever emailed in production) and confirms it via the real endpoint.
    private void registerAndVerify(String username, String email) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username(username)
                .email(email)
                .password("password123")
                .phoneNumber("08012345678")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        String signupCode = emailVerificationService.generateAndSendCode(email, VerificationPurpose.SIGNUP).code();
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + signupCode + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void register_returns201WithUnverifiedUser_whenRequestIsValid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("olamide"))
                .andExpect(jsonPath("$.user.email").value("olamide@student.lautech.edu.ng"))
                // New accounts can't log in until the emailed code is confirmed.
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                // No token is handed out at registration anymore — you log in
                // separately (password or emailed code).
                .andExpect(jsonPath("$.token").doesNotExist())
                // The password hash must never appear anywhere in the response.
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void register_returns409_whenEmailAlreadyRegistered() throws Exception {
        RegisterRequest first = validRegisterRequest();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)));

        RegisterRequest duplicate = RegisterRequest.builder()
                .username("different-username")
                .email("olamide@student.lautech.edu.ng") // same email
                .password("password456")
                .phoneNumber("08099999999")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void register_returns400WithFieldErrors_whenRequestIsInvalid() throws Exception {
        RegisterRequest invalid = RegisterRequest.builder()
                .username("ab") // too short
                .email("not-an-email")
                .password("short") // too short
                .phoneNumber("123") // too short
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void register_returns400_whenPasswordIsTooSimilarToUsername() throws Exception {
        RegisterRequest similar = RegisterRequest.builder()
                .username("johnsmith")
                .email("similar@example.com")
                .password("johnsmith123") // contains the username
                .phoneNumber("08012345678")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(similar)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Password must not be the same as or too similar to your username"));
    }

    @Test
    void register_returns400_whenPasswordIsSameAsUsername() throws Exception {
        RegisterRequest same = RegisterRequest.builder()
                .username("john1smith")
                .email("same@example.com")
                .password("john1smith") // identical to the username
                .phoneNumber("08012345678")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(same)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEmail_returns200AndUnlocksAccount_whenCodeValid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())));

        String code = emailVerificationService.generateAndSendCode(
                "olamide@student.lautech.edu.ng", VerificationPurpose.SIGNUP).code();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olamide@student.lautech.edu.ng\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("olamide@student.lautech.edu.ng").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void verifyEmail_returns400_whenCodeInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest())));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olamide@student.lautech.edu.ng\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginFlow_requestsCode_andExchangesItForToken_whenAccountVerified() throws Exception {
        registerAndVerify("loginuser", "loginuser@example.com");

        mockMvc.perform(post("/api/auth/login-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"loginuser@example.com\"}"))
                .andExpect(status().isOk());

        String loginCode = emailVerificationService.generateAndSendCode(
                "loginuser@example.com", VerificationPurpose.LOGIN).code();

        mockMvc.perform(post("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"loginuser@example.com\",\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.emailVerified").value(true));
    }

    @Test
    void login_returns200WithToken_whenPasswordCorrect() throws Exception {
        registerAndVerify("pwlogin", "pwlogin@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pwlogin@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("pwlogin@example.com"));
    }

    @Test
    void login_returns401_whenPasswordWrong() throws Exception {
        registerAndVerify("wronpw", "wrongpw@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongpw@example.com\",\"password\":\"definitely-wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // Unverified and unknown accounts must produce the same 401 as a wrong
    // password so the endpoint can't be used to enumerate users.
    @Test
    void login_returns401_whenAccountNotVerified() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest())));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olamide@student.lautech.edu.ng\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_returns401_forUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void checkUsername_returnsAvailable_whenFree() throws Exception {
        mockMvc.perform(get("/api/auth/check-username").param("username", "brandnew"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void checkUsername_returnsTaken_whenInUse() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest())));

        mockMvc.perform(get("/api/auth/check-username").param("username", "olamide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void checkUsername_returnsUnavailable_whenTooShort() throws Exception {
        mockMvc.perform(get("/api/auth/check-username").param("username", "ab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void loginWithCode_returns401_whenAccountNotVerified() throws Exception {
        // Register only — never confirm the emailed code.
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest())));

        String loginCode = emailVerificationService.generateAndSendCode(
                "olamide@student.lautech.edu.ng", VerificationPurpose.LOGIN).code();

        mockMvc.perform(post("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olamide@student.lautech.edu.ng\",\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithCode_returns401_whenCodeIsWrong() throws Exception {
        registerAndVerify("wrongcode", "wrongcode@example.com");

        emailVerificationService.generateAndSendCode("wrongcode@example.com", VerificationPurpose.LOGIN).code();

        mockMvc.perform(post("/api/auth/login-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongcode@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    // The response must be identical for an unregistered address so the
    // endpoint can't be used to probe which emails have accounts.
    @Test
    void loginCode_returns200_forUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPassword_returns200_forKnownEmail() throws Exception {
        registerAndVerify("forgotuser", "forgotuser@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"forgotuser@example.com\"}"))
                .andExpect(status().isOk());
    }

    // The response must be identical for an unregistered address so the
    // endpoint can't be used to probe which emails have accounts.
    @Test
    void forgotPassword_returns200_forUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_replacesPassword_whenCodeValid() throws Exception {
        registerAndVerify("resetuser", "resetuser@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"resetuser@example.com\"}"))
                .andExpect(status().isOk());

        String resetCode = emailVerificationService.generateAndSendCode(
                "resetuser@example.com", VerificationPurpose.RESET).code();

        ResetPasswordRequest reset = ResetPasswordRequest.builder()
                .email("resetuser@example.com")
                .code(resetCode)
                .newPassword("newPassword456")
                .build();
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reset)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("resetuser@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("newPassword456", user.getPassword()))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("password123", user.getPassword()))
                .isFalse();
    }

    @Test
    void resetPassword_returns400_forUnknownCode() throws Exception {
        ResetPasswordRequest reset = ResetPasswordRequest.builder()
                .email("nobody@example.com")
                .code("000000")
                .newPassword("newPassword456")
                .build();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reset)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_returns400_whenCodeExpired() throws Exception {
        registerAndVerify("expireduser", "expireduser@example.com");

        User user = userRepository.findByEmail("expireduser@example.com").orElseThrow();
        codeRepository.save(EmailVerificationCode.builder()
                .email("expireduser@example.com")
                .purpose(VerificationPurpose.RESET)
                .codeHash(EmailVerificationService.hash("expired-code-123"))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .used(false)
                .attempts(0)
                .build());

        ResetPasswordRequest reset = ResetPasswordRequest.builder()
                .email("expireduser@example.com")
                .code("expired-code-123")
                .newPassword("newPassword456")
                .build();
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reset)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPassword_returns400_whenNewPasswordTooSimilarToUsername() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("resetjack")
                .email("similarreset@example.com")
                .password("secret123")
                .phoneNumber("08012345678")
                .build();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        String signupCode = emailVerificationService.generateAndSendCode(
                "similarreset@example.com", VerificationPurpose.SIGNUP).code();
        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"similarreset@example.com\",\"code\":\"" + signupCode + "\"}"))
                .andExpect(status().isOk());

        String resetCode = emailVerificationService.generateAndSendCode(
                "similarreset@example.com", VerificationPurpose.RESET).code();

        ResetPasswordRequest reset = ResetPasswordRequest.builder()
                .email("similarreset@example.com")
                .code(resetCode)
                .newPassword("resetjack2024") // contains the username
                .build();
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reset)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Password must not be the same as or too similar to your username"));
    }

    @Test
    void resendVerificationCode_returns200_forUnverifiedAccount() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequest())));

        mockMvc.perform(post("/api/auth/resend-verification-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olamide@student.lautech.edu.ng\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resendVerificationCode_returns200_forUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void config_returnsGoogleDisabled_whenNotConfigured() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleEnabled").value(false))
                .andExpect(jsonPath("$.googleAuthorizationUrl")
                        .value("http://localhost:8080/oauth2/authorization/google"));
    }
}
