package com.aucolher.api.user;

import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ConflictException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.dto.ProfileUpdateDTO;
import com.aucolher.api.user.dto.PublicProfileDTO;
import com.aucolher.api.user.dto.UserResponseDTO;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.VisitingHour;
import com.aucolher.api.user.entity.TeamMember;
import com.aucolher.api.user.entity.UserType;
import com.aucolher.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Regras do perfil da própria conta ("Minha conta"). Assim como na
 * AuthService, o DTO chega já validado e normalizado.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Perfil completo da conta, com equipe e horários. Transacional porque as
     * coleções são lazy — quem chama de fora de uma transação (o handler do
     * login Google) não conseguiria carregá-las.
     */
    @Transactional(readOnly = true)
    public UserResponseDTO getProfile(String email) {
        return userRepository.findByEmail(email)
                .map(UserResponseDTO::from)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
    }

    /**
     * Perfil público de qualquer conta ativa. Admin não tem perfil público, e
     * conta banida some do site: os dois casos respondem 404, como um id que
     * não existe. O admin ainda vê o perfil banido (com `active: false`), para
     * conferir e reativar a conta.
     *
     * @param viewerEmail e-mail de quem está vendo, ou null se não estiver logado
     */
    @Transactional(readOnly = true)
    public PublicProfileDTO getPublicProfile(Long id, String viewerEmail) {
        return userRepository.findById(id)
                .filter(user -> Boolean.TRUE.equals(user.getActive()) || isAdmin(viewerEmail))
                .filter(user -> user.getUserType() != UserType.ADMIN)
                .map(PublicProfileDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Perfil não encontrado"));
    }

    /**
     * Substitui o perfil editável da conta autenticada. Usuário comum grava só
     * nome, foto, bio e CEP/cidade/UF; ONG grava também o perfil institucional
     * (com o banner) e o endereço completo, que continua obrigatório.
     */
    @Transactional
    public UserResponseDTO updateProfile(String email, ProfileUpdateDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
        applyProfile(user, dto);
        return UserResponseDTO.from(user);
    }

    /** Moderação: a conta inteira de uma pessoa ou ONG, com o e-mail de login, para o admin editar. */
    @Transactional(readOnly = true)
    public User findForModeration(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));
        if (user.getUserType() == UserType.ADMIN) {
            throw new BusinessException("Contas de admin não são editadas pelo painel.");
        }
        return user;
    }

    /**
     * Moderação: o admin edita o perfil de outra conta (as mesmas regras de
     * "Minha conta") e, se preciso, o e-mail de login. Trocar o e-mail
     * derruba a sessão da conta (o token é pelo e-mail): ela entra de novo
     * com o e-mail novo. Conta Google não troca de e-mail, porque o próximo
     * login pelo Google criaria outra conta. Conta excluída não é editada:
     * ela foi anonimizada de propósito.
     */
    @Transactional
    public User updateByModeration(Long id, String email, ProfileUpdateDTO dto, Long adminId) {
        User user = findForModeration(id);
        if (user.isDeleted()) {
            throw new BusinessException("Contas excluídas não podem ser editadas.");
        }
        changeEmail(user, email);
        applyProfile(user, dto);
        user.markModeratedBy(adminId);
        return user;
    }

    private void changeEmail(User user, String email) {
        if (email.equals(user.getEmail())) return;
        if (user.getProvider() == AuthProvider.GOOGLE) {
            throw new BusinessException("Esta conta entra pelo Google: o e-mail vem do Google e não pode ser alterado.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Este e-mail já está em uso por outra conta.");
        }
        user.setEmail(email);
    }

    /** Substitui o perfil editável (o mesmo para a própria conta e para a moderação). */
    private void applyProfile(User user, ProfileUpdateDTO dto) {
        if (user.getUserType() != UserType.NGO && dto.coverUrl() != null) {
            throw new BusinessException("Só ONGs podem ter banner no perfil.");
        }

        user.setName(dto.name());
        user.setPhotoUrl(dto.photoUrl());
        user.setBio(dto.bio());
        user.setCep(dto.cep());
        user.setCity(dto.city());
        user.setState(dto.state());

        if (user.getUserType() == UserType.NGO) {
            updateNgoProfile(user, dto);
        }
    }

    private void updateNgoProfile(User user, ProfileUpdateDTO dto) {
        boolean incompleteAddress = Stream.of(dto.cep(), dto.street(), dto.number(), dto.district(), dto.city(), dto.state())
                .anyMatch(Objects::isNull);
        if (incompleteAddress) {
            throw new BusinessException("O endereço da ONG é obrigatório: informe CEP, logradouro, número, bairro, cidade e UF");
        }

        user.setCoverUrl(dto.coverUrl());
        user.setInstagram(dto.instagram());
        user.setTwitter(dto.twitter());
        user.setFacebook(dto.facebook());
        user.setFoundedYear(dto.foundedYear());
        user.setStreet(dto.street());
        user.setNumber(dto.number());
        user.setComplement(dto.complement());
        user.setDistrict(dto.district());

        // A ordem da lista vira a coluna "sort_order" (@OrderColumn)
        user.getTeam().clear();
        dto.team().forEach(member -> user.getTeam().add(new TeamMember(member.name(), member.role())));

        user.getVisitingHours().clear();
        dto.visitingHours().forEach(slot -> user.getVisitingHours().add(new VisitingHour(slot.days(), slot.hours())));
    }

    private boolean isAdmin(String email) {
        return email != null && userRepository.findByEmail(email)
                .map(user -> user.getUserType() == UserType.ADMIN)
                .orElse(false);
    }
}
