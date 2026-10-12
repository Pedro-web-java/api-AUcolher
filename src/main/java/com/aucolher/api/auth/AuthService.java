package com.aucolher.api.auth;

import com.aucolher.api.auth.dto.AuthResponseDTO;
import com.aucolher.api.auth.dto.NgoRegistrationDTO;
import com.aucolher.api.auth.dto.PersonRegistrationDTO;
import com.aucolher.api.auth.dto.LoginDTO;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.InvalidCredentialsException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.dto.UserResponseDTO;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.UserType;
import com.aucolher.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de cadastro e autenticação. Os DTOs chegam aqui já validados
 * (Bean Validation) e normalizados (construtores compactos dos records),
 * então esta classe cuida só das regras de negócio.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponseDTO registerNgo(NgoRegistrationDTO dto) {
        ensureEmailAvailable(dto.email());

        if (userRepository.existsByCnpj(dto.cnpj())) {
            throw new BusinessException("Já existe uma ONG cadastrada com este CNPJ");
        }

        // Sem etapa de aprovação (seção 7 das regras de negócio): a ONG já
        // nasce ativa e com o selo de verificada
        User user = User.builder()
                .name(dto.name())
                .email(dto.email())
                .password(passwordEncoder.encode(dto.password()))
                .cnpj(dto.cnpj())
                .userType(UserType.NGO)
                .provider(AuthProvider.LOCAL)
                .active(true)
                .verified(true)
                .photoUrl(dto.photoUrl())
                .bio(dto.bio())
                .institutionalEmail(dto.institutionalEmail())
                .instagram(dto.instagram())
                .twitter(dto.twitter())
                .facebook(dto.facebook())
                .foundedYear(dto.foundedYear())
                .cep(dto.cep())
                .street(dto.street())
                .number(dto.number())
                .complement(dto.complement())
                .district(dto.district())
                .city(dto.city())
                .state(dto.state())
                .build();

        return buildResponse(userRepository.save(user));
    }

    @Transactional
    public AuthResponseDTO registerPerson(PersonRegistrationDTO dto) {
        ensureEmailAvailable(dto.email());

        User user = User.builder()
                .name(dto.name())
                .email(dto.email())
                .password(passwordEncoder.encode(dto.password()))
                .userType(UserType.PERSON)
                .provider(AuthProvider.LOCAL)
                .active(true)
                .photoUrl(dto.photoUrl())
                .build();

        return buildResponse(userRepository.save(user));
    }

    /**
     * Login único para qualquer tipo de conta: valida só e-mail e senha.
     * O papel (userType) vai no payload da resposta, e é o frontend que
     * decide o que mostrar a partir dele.
     */
    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginDTO dto) {
        return buildResponse(authenticate(dto));
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Já existe um usuário cadastrado com este e-mail");
        }
    }

    private User authenticate(LoginDTO dto) {
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new InvalidCredentialsException("E-mail ou senha inválidos"));

        if (user.getProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            throw new InvalidCredentialsException("Esta conta utiliza login via Google. Use a autenticação OAuth2");
        }

        if (!user.getActive()) {
            throw new InvalidCredentialsException("Usuário inativo");
        }

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new InvalidCredentialsException("E-mail ou senha inválidos");
        }

        return user;
    }

    private AuthResponseDTO buildResponse(User user) {
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponseDTO(token, UserResponseDTO.from(user));
    }
}
