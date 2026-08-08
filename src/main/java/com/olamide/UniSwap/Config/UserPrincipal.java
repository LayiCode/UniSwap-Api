package com.olamide.UniSwap.Config;

import com.olamide.UniSwap.Entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Adapts our User entity to Spring Security's UserDetails contract, without
// making the entity itself implement UserDetails (keeps persistence and
// security concerns separate).
@Getter
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    // The actual application-level id, used everywhere we need "who is this
    // request acting as" (e.g. ProductService ownership checks).
    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Two implicit roles. Everyone gets ROLE_USER (the minimum, useful for
        // hasRole-based rules later), and staff accounts additionally get
        // ROLE_ADMIN which unlocks the moderation endpoints. The flag is read
        // from the entity each request (CustomUserDetailsService reloads it),
        // so a freshly promoted account takes effect immediately.
        if (user.isAdmin()) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        // We authenticate by email, not the display username, so this
        // returns email deliberately despite the method name.
        return user.getEmail();
    }
}