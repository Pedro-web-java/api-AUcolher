package com.aucolher.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** O filtro do JWT: token de conta banida deixa de valer na hora, sem esperar vencer. */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private FilterChain chain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, new ObjectMapper().findAndRegisterModules());
        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        response = new MockHttpServletResponse();
        when(jwtService.extractEmail("token")).thenReturn("person@email.com");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenAuthenticates() throws Exception {
        when(userDetailsService.loadUserByUsername("person@email.com")).thenReturn(account(false));
        when(jwtService.isTokenValid("token", "person@email.com")).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void bannedAccountGets403WithTheSuspensionMessage() throws Exception {
        when(userDetailsService.loadUserByUsername("person@email.com")).thenReturn(account(true));
        when(jwtService.isTokenValid("token", "person@email.com")).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains(SuspendedAccount.MESSAGE);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }

    @Test
    void tokenOfADeletedAccountGoesOnUnauthenticated() throws Exception {
        when(userDetailsService.loadUserByUsername("person@email.com")).thenThrow(new UsernameNotFoundException("x"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    private static org.springframework.security.core.userdetails.UserDetails account(boolean disabled) {
        return User.withUsername("person@email.com").password("").roles("PERSON").disabled(disabled).build();
    }
}
