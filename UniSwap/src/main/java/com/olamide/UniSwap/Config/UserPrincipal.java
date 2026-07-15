package com.olamide.UniSwap.Config;

import com.olamide.UniSwap.Entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
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
        // No roles/permissions system yet — every authenticated user has the
        // same single implicit role. Revisit if you add e.g. an ADMIN role.
        return List.of();
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