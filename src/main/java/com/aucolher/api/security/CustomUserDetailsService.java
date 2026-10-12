package com.aucolher.api.security;

import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));

        // Nome completo: User sozinho aqui é a nossa entidade (com.aucolher.api.user.entity.User)
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword() == null ? "" : user.getPassword())
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getUserType().name()))
                .disabled(!user.getActive())
                .build();
    }
}
