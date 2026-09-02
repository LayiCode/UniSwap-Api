package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Config.UserPrincipal;
import com.olamide.UniSwap.Dto.*;
import com.olamide.UniSwap.Entity.ProductStatus;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.ProductRepository;
import com.olamide.UniSwap.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final FileStorageService fileStorageService;

    // Creates the account (emailVerified=false) and emails a signup code.
    // Login is locked until /auth/verify-email confirms the code.
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Normalize email (trim + lowercase) BEFORE both the uniqueness check
        // and the save, so "Olamide@X.com" and "olamide@x.com" can't become
        // two separate accounts that confuse login.
        String email = normalizeEmail(request.getEmail());
        // Username is optional: when blank we derive a collision-free
        // placeholder so the user can sign up now and pick a real name later.
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword();
        String phoneNumber = normalizePhone(request.getPhoneNumber());

        validateCredentials(username, password, null);

        if (username.isBlank()) {
            username = uniqueUsername(null, email);
        } else if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .phoneNumber(phoneNumber)
                .emailVerified(false)
                .build();

        User saved = userRepository.save(user);

        CodeDelivery delivery = emailVerificationService.generateAndSendCode(saved.getEmail(), VerificationPurpose.SIGNUP);

        String message = delivery.delivered()
                ? "Account created. Check your email for the verification code to activate your login."
                : "Account created. We couldn't email your verification code, so it's shown below.";

        return RegisterResponse.builder()
                .user(UserResponseDTO.fromEntity(saved))
                .message(message)
                .verificationCode(delivery.delivered() ? null : delivery.code())
                .build();
    }

    // Password login: same 401 "Invalid email or password" for an unknown
    // email, a wrong password, AND an unverified account — callers can't tell
    // which case it was, so the endpoint can't be used to enumerate users.
    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequest request) {
        String normalized = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return buildAuthResponse(user);
    }

    // Live "is this username free?" check for the register form. Blank and
    // too-short values are rejected before hitting the database.
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        String trimmed = username == null ? "" : username.trim();
        if (trimmed.length() < 3 || trimmed.length() > 30) {
            return false;
        }
        return !userRepository.existsByUsername(trimmed);
    }

    @Transactional
    public User verifyEmail(String email, String code) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification attempt"));

        if (!emailVerificationService.verifyCode(normalized, VerificationPurpose.SIGNUP, code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
        }

        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    @Transactional
    public CodeDelivery resendSignupCode(String email) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null || user.isEmailVerified()) {
            // Same response for unknown and already-verified accounts
            // (anti-enumeration).
            return null;
        }
        return emailVerificationService.generateAndSendCode(normalized, VerificationPurpose.SIGNUP);
    }

    // Passwordless login step 1: email a one-time code to the address. Returns
    // the delivery result when a code was generated, or null for an unknown /
    // unverified address (same null for both, anti-enumeration).
    @Transactional
    public CodeDelivery requestLoginCode(String email) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null || !user.isEmailVerified()) {
            // Same response for unknown and unverified accounts (anti-enumeration).
            return null;
        }
        return emailVerificationService.generateAndSendCode(normalized, VerificationPurpose.LOGIN);
    }

    // Passwordless login step 2: exchange the code for a JWT.
    @Transactional(readOnly = true)
    public AuthResponseDTO loginWithCode(String email, String code) {
        String normalized = normalizeEmail(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or code"));
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email not verified");
        }
        if (!emailVerificationService.verifyCode(normalized, VerificationPurpose.LOGIN, code)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or code");
        }
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isDeleted()) {
            // A deleted (soft-deleted) account is indistinguishable from one
            // that never existed — hides the profile, blocks messaging, etc.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return user;
    }

    // Soft-delete the account: keep the row (preserving FK integrity for
    // chat/purchase/report history) but mark it deleted and anonymize the
    // email + username so the original identifiers become free to re-register.
    // The anonymized email never matches login lookups, so the account can't
    // be signed into again.
    @Transactional
    public void deactivate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is already deleted");
        }
        user.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        user.setEmail("deleted-" + id + "@local.invalid");
        user.setUsername("deleted-user-" + id);
        user.setDisplayName(null);
        user.setAvatarUrl(null);
        user.setBio(null);
        user.setLocation(null);
        userRepository.save(user);
    }

    // Updates only the profile fields the caller actually provided. Null means
    // "leave unchanged", so partial updates never wipe a value the client
    // didn't send. Username is optional and NOT uniqueness-checked here (that
    // only happens at signup); a blank username means "keep the current".
    @Transactional
    public User updateProfile(Long id, UpdateProfileRequest request) {
        User user = getById(id);
        if (request.getUsername() != null) {
            String username = request.getUsername().trim();
            if (!username.isBlank()) {
                user.setUsername(username);
            }
        }
        if (request.getPhoneNumber() != null) {
            String phone = normalizePhone(request.getPhoneNumber());
            if (phone != null && !phone.isBlank()) {
                user.setPhoneNumber(phone);
            }
        }
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        if (request.getLocation() != null) {
            user.setLocation(request.getLocation().trim());
        }
        return userRepository.save(user);
    }

    // Changes the current user's password. Requires the current password to
    // match (proves the caller knows the account secret), and reuses the same
    // password-quality rules as register/reset (length, not identical to the
    // current one, not similar to the username).
    @Transactional
    public User changePassword(Long id, ChangePasswordRequest request) {
        User user = getById(id);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        validateCredentials(user.getUsername(), request.getNewPassword(), user.getPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        return userRepository.save(user);
    }

    // Replaces the current avatar with a freshly uploaded image (reusing the
    // same magic-byte-validated storage path as product photos). The previous
    // avatar file is best-effort deleted when it's no longer referenced.
    @Transactional
    public User uploadAvatar(Long id, MultipartFile file) {
        User user = getById(id);
        String oldAvatar = user.getAvatarUrl();
        String newAvatar = fileStorageService.store(file);
        user.setAvatarUrl(newAvatar);
        User saved = userRepository.save(user);
        if (oldAvatar != null && !oldAvatar.equals(newAvatar)) {
            fileStorageService.delete(oldAvatar);
        }
        return saved;
    }

    // Number of listings the user currently has live (status AVAILABLE). Used
    // to show an "active listings" count on a public profile.
    public long countActiveListings(Long userId) {
        return productRepository.countBySellerIdAndStatus(userId, ProductStatus.AVAILABLE);
    }

    // Google sign-in: reuse an existing account if the verified email is
    // already registered, otherwise provision one. The password is a random
    // value that will never be used — Google is the only way in for these
    // accounts unless the user later does a password reset (which replaces it).
    @Transactional
    public User findOrCreateOAuthUser(String email, String displayName) {
        String normalized = normalizeEmail(email);
        User existing = userRepository.findByEmail(normalized).orElse(null);
        if (existing != null) {
            // A legacy account created before email verification existed is
            // treated as verified the moment a Google login succeeds for it.
            if (!existing.isEmailVerified()) {
                existing.setEmailVerified(true);
                existing = userRepository.save(existing);
            }
            return existing;
        }
        User user = User.builder()
                .username(uniqueUsername(displayName, normalized))
                .email(normalized)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .phoneNumber("")
                .emailVerified(true)
                .build();
        return userRepository.save(user);
    }

    // Shared rules for setting a password (register + reset): it must not be
    // the same as, contain, or closely resemble the username. Only password
    // quality rules live here; confirmation matching is the client's job. A
    // blank username (optional at signup) just skips the similarity check.
    public void validateCredentials(String username, String password, String currentEncodedPassword) {
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }
        if (currentEncodedPassword != null && passwordEncoder.matches(password, currentEncodedPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from the current one");
        }
        if (username != null && !username.isBlank()
                && PasswordSimilarityValidator.isRejected(username, password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must not be the same as or too similar to your username");
        }
    }

    // Derives a collision-free username from the Google profile name, falling
    // back to the email prefix when the name is empty or unusable.
    private String uniqueUsername(String displayName, String email) {
        String base = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (base.length() < 3) {
            base = email.split("@")[0].replaceAll("[^a-z0-9_]", "");
        }
        if (base.isBlank()) {
            base = "user";
        }
        base = base.substring(0, Math.min(base.length(), 24));

        String candidate = base;
        int suffix = 2;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + (suffix++);
        }
        return candidate;
    }

    private AuthResponseDTO buildAuthResponse(User user) {
        String token = jwtService.generateToken(new UserPrincipal(user));
        return AuthResponseDTO.builder()
                .token(token)
                .user(UserResponseDTO.fromEntity(user))
                .build();
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // Normalize a phone number to a compact digits-only form so equivalent
    // formats are stored consistently: "+234 801 234 5678", "+234-801-234-5678"
    // and "08012345678" all become their plain digit string. The leading "+"
    // is dropped but the national 234 (or the leading 0) is preserved exactly
    // as the user typed it. An input that contains no digits at all is kept as
    // the trimmed original (so the DTO @Pattern still has a chance to reject it).
    static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone == null ? null : phone.trim();
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? phone.trim() : digits;
    }
}
