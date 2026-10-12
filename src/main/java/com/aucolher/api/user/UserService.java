package com.aucolher.api.user;

import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.user.dto.ProfileUpdateDTO;
import com.aucolher.api.user.dto.UserResponseDTO;
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
     * Substitui o perfil editável da conta autenticada. Usuário comum grava só
     * nome, foto, bio e CEP/cidade/UF; ONG grava também o perfil institucional
     * e o endereço completo, que continua obrigatório.
     */
    @Transactional
    public UserResponseDTO updateProfile(String email, ProfileUpdateDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));

        user.setName(dto.name());
        user.setPhotoUrl(dto.photoUrl());
        user.setBio(dto.bio());
        user.setCep(dto.cep());
        user.setCity(dto.city());
        user.setState(dto.state());

        if (user.getUserType() == UserType.NGO) {
            updateNgoProfile(user, dto);
        }

        return UserResponseDTO.from(user);
    }

    private void updateNgoProfile(User user, ProfileUpdateDTO dto) {
        boolean incompleteAddress = Stream.of(dto.cep(), dto.street(), dto.number(), dto.district(), dto.city(), dto.state())
                .anyMatch(Objects::isNull);
        if (incompleteAddress) {
            throw new BusinessException("O endereço da ONG é obrigatório: informe CEP, logradouro, número, bairro, cidade e UF");
        }

        user.setInstitutionalEmail(dto.institutionalEmail());
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
}
