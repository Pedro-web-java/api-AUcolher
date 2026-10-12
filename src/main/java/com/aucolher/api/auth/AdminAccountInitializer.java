package com.aucolher.api.auth;

import com.aucolher.api.shared.validation.Sanitizer;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria a primeira conta ADMIN quando a API sobe. Não existe cadastro
 * público de admin (seção 2 das regras de negócio): e-mail e senha vêm
 * das variáveis de ambiente ADMIN_EMAIL e ADMIN_PASSWORD, então nenhuma
 * senha fica no repositório.
 *
 * Só cria quando o e-mail ainda não existe e nunca altera uma conta já
 * gravada: trocar ADMIN_PASSWORD depois não muda a senha de quem já existe,
 * e um e-mail que já é de pessoa ou ONG não vira admin.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    /** A conta com mais poder da plataforma pede mais que os 6 do cadastro público. */
    static final int MIN_PASSWORD_LENGTH = 8;

    private static final String DEFAULT_NAME = "Administrador";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public AdminAccountInitializer(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.email:}") String email,
                                   @Value("${app.admin.password:}") String password,
                                   @Value("${app.admin.name:}") String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        // Mesma limpeza do login (LoginDTO): é assim que o e-mail vai ser buscado
        this.email = Sanitizer.text(email);
        this.password = password;
        String cleanedName = Sanitizer.text(name);
        this.name = cleanedName == null ? DEFAULT_NAME : cleanedName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || password == null || password.isBlank()) {
            log.warn("ADMIN_EMAIL e ADMIN_PASSWORD não definidos: nenhuma conta admin foi criada. " +
                    "Defina as duas variáveis no ambiente para criar o primeiro admin.");
            return;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD precisa ter no mínimo {} caracteres: a conta admin não foi criada.",
                    MIN_PASSWORD_LENGTH);
            return;
        }

        userRepository.findByEmail(email).ifPresentOrElse(
                existing -> {
                    if (existing.getUserType() != UserType.ADMIN) {
                        log.warn("ADMIN_EMAIL ({}) já pertence a uma conta {}: ela não foi transformada em admin. " +
                                "Use outro e-mail.", email, existing.getUserType());
                    }
                },
                this::createAdmin
        );
    }

    private void createAdmin() {
        userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .userType(UserType.ADMIN)
                .provider(AuthProvider.LOCAL)
                .active(true)
                .build());

        log.info("Conta admin criada para {}.", email);
    }
}
