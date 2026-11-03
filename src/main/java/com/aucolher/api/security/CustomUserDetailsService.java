package com.aucolher.api.security;

import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public AuthenticatedUser loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));

        return new AuthenticatedUser(
                user.getEmail(),
                user.getPassword() == null ? "" : user.getPassword(),
                user.getActive(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getUserType().name())),
                user.getPasswordChangedAt());
    }
}
