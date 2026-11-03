package com.aucolher.api.user.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Anonimização da conta excluída: a linha fica, os dados pessoais não. */
class UserTest {

    @Test
    void anonymizeKeepsTheRowButRemovesEveryPersonalData() {
        User ngo = User.builder()
                .id(7L).name("ONG Patinhas").email("ong@email.com").password("$2a$hash")
                .userType(UserType.NGO).cnpj("11222333000181").verified(true)
                .photoUrl("foto").coverUrl("banner").bio("Bio")
                .instagram("patinhas").twitter("patinhas").facebook("https://facebook.com/patinhas").foundedYear(2010)
                .team(new ArrayList<>(List.of(new TeamMember("Ana", "Presidente"))))
                .visitingHours(new ArrayList<>(List.of(new VisitingHour("Sábado", "9h às 12h"))))
                .cep("88900000").street("Rua A").number("10").complement("Sala 2").district("Centro")
                .city("Araranguá").state("SC")
                .build();
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 10, 0);

        ngo.anonymize(now);

        assertThat(ngo.getId()).isEqualTo(7L);
        assertThat(ngo.getUserType()).isEqualTo(UserType.NGO);
        assertThat(ngo.getName()).isEqualTo("Conta excluída");
        assertThat(ngo.getEmail()).isEqualTo("excluida-7@aucolher.invalid");
        assertThat(ngo.getPassword()).isNull();
        assertThat(ngo.getCnpj()).isNull();
        assertThat(ngo.getPhotoUrl()).isNull();
        assertThat(ngo.getCoverUrl()).isNull();
        assertThat(ngo.getBio()).isNull();
        assertThat(ngo.getInstagram()).isNull();
        assertThat(ngo.getTwitter()).isNull();
        assertThat(ngo.getFacebook()).isNull();
        assertThat(ngo.getFoundedYear()).isNull();
        assertThat(ngo.getTeam()).isEmpty();
        assertThat(ngo.getVisitingHours()).isEmpty();
        assertThat(ngo.getCep()).isNull();
        assertThat(ngo.getStreet()).isNull();
        assertThat(ngo.getNumber()).isNull();
        assertThat(ngo.getComplement()).isNull();
        assertThat(ngo.getDistrict()).isNull();
        assertThat(ngo.getCity()).isNull();
        assertThat(ngo.getState()).isNull();
        assertThat(ngo.getVerified()).isFalse();
        assertThat(ngo.getActive()).isFalse();
        assertThat(ngo.getDeletedAt()).isEqualTo(now);
        assertThat(ngo.isDeleted()).isTrue();
    }

    @Test
    void aNewAccountIsNotDeleted() {
        assertThat(User.builder().id(1L).build().isDeleted()).isFalse();
    }
}
