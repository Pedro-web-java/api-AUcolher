package com.aucolher.api.config;

import com.aucolher.api.security.CustomOAuth2UserService;
import com.aucolher.api.security.JwtAuthenticationFilter;
import com.aucolher.api.security.OAuth2AuthenticationSuccessHandler;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** Origens liberadas no CORS — o endereço do frontend, por ambiente. */
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // API REST stateless: CSRF desativado (não há sessão/cookie de formulário a proteger)
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Repasse interno para a página de erro (/error): o filtro do JWT
                // não roda de novo nele, então sem isto um 403 (ex.: pessoa em
                // /api/admin) chegava ao frontend como 401 — e o 401 desloga
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // Cadastro e login são públicos; o callback do Google precisa
                // ser público para o Spring Security concluir o fluxo OAuth2
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // "Meus animais" vem antes da regra pública: "/api/animals/*" também casaria com ele
                .requestMatchers(HttpMethod.GET, "/api/animals/mine").authenticated()
                // Vitrine pública: listagem, detalhes e animais de um perfil, sem login
                .requestMatchers(HttpMethod.GET, "/api/animals", "/api/animals/*", "/api/users/*/animals").permitAll()
                // Eventos: listagem e detalhe são públicos; "mine" e "attending" vêm antes
                // porque "/api/events/*" também casaria com eles
                .requestMatchers(HttpMethod.GET, "/api/events/mine", "/api/events/attending").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*").permitAll()
                // AUmatch (quiz e deck): só pessoa física, que é quem adota.
                // Sem token → 401; ONG ou admin → 403
                .requestMatchers("/api/match/**").hasRole("PERSON")
                // Painel do admin: só a conta ADMIN (a autoridade ROLE_ADMIN vem do
                // CustomUserDetailsService). Sem token → 401; logado sem ser admin → 403
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // Sem isto, uma requisição sem token para rota protegida seria
            // redirecionada para a tela de login do Google em vez de receber 401
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2AuthenticationSuccessHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
