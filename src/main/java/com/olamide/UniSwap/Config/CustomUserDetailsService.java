package com.olamide.UniSwap.Config;

import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Tokens carry the normalized (trimmed, lowercased) email as subject;
        // normalize the lookup key too so edge-case input can't miss the row.
        String normalized = com.olamide.UniSwap.Service.UserService.normalizeEmail(email);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + email));
        return new UserPrincipal(user);
    }
}