package com.aucolher.api.user.dto;

import com.aucolher.api.shared.validation.PhotoUrl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Validação do banner no PUT /api/users/me (as anotações do ProfileUpdateDTO). */
class ProfileUpdateDTOTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static ProfileUpdateDTO withCover(String coverUrl) {
        return new ProfileUpdateDTO("Patinhas", null, coverUrl, null, null, null, null, null, List.of(), List.of(),
                "88900000", "Rua A", "10", null, "Centro", "Araranguá", "SC");
    }

    private static Set<String> messages(ProfileUpdateDTO dto) {
        Set<ConstraintViolation<ProfileUpdateDTO>> violations = validator.validate(dto);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void acceptsAnImageDataUrlOrALink() {
        assertThat(messages(withCover("data:image/webp;base64,AAAA"))).isEmpty();
        assertThat(messages(withCover("https://exemplo.com/banner.jpg"))).isEmpty();
        assertThat(messages(withCover(null))).isEmpty();
    }

    @Test
    void rejectsOtherFormats() {
        assertThat(messages(withCover("data:image/gif;base64,AAAA")))
                .containsExactly("Envie o banner como imagem JPG, PNG ou WebP.");
    }

    @Test
    void rejectsABannerAboveTheLimit() {
        String tooBig = "data:image/jpeg;base64," + "A".repeat(PhotoUrl.COVER_MAX_LENGTH);

        assertThat(messages(withCover(tooBig))).containsExactly("O banner é grande demais.");
    }
}
