package com.aucolher.api.animal;

import com.aucolher.api.animal.entity.AnimalSize;
import com.aucolher.api.config.SecurityConfig;
import com.aucolher.api.security.CustomOAuth2UserService;
import com.aucolher.api.security.CustomUserDetailsService;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.security.OAuth2AuthenticationSuccessHandler;
import com.aucolher.api.shared.dto.PageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Camada HTTP das rotas de animais: quais são públicas, quais exigem login e
 * como a validação do corpo responde. A Service é simulada — as regras de
 * negócio estão em AnimalServiceTest.
 */
@WebMvcTest(AnimalController.class)
@Import(SecurityConfig.class)
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnimalService animalService;

    // Dependências da configuração de segurança real, importada acima
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Test
    void listingIsPublic() throws Exception {
        when(animalService.listAvailable(any(), anyInt(), anyInt()))
                .thenReturn(new PageDTO<>(List.of(), 0, 12, 0, 0));

        mockMvc.perform(get("/api/animals").param("species", "CAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void profileAnimalsArePublic() throws Exception {
        mockMvc.perform(get("/api/users/1/animals")).andExpect(status().isOk());
    }

    @Test
    void sizesFilterDoesNotClashWithPageSize() throws Exception {
        // `size` é o tamanho da página; o filtro de porte é `sizes`
        mockMvc.perform(get("/api/animals").param("sizes", "SMALL").param("size", "12"))
                .andExpect(status().isOk());

        verify(animalService).listAvailable(argThat(filter -> filter.sizes().equals(List.of(AnimalSize.SMALL))), eq(0), eq(12));
    }

    @Test
    void filterWithUnknownValueReturns400() throws Exception {
        mockMvc.perform(get("/api/animals").param("species", "BIRD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.species").exists());
    }

    @Test
    void myAnimalsRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/animals/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "owner@email.com")
    void myAnimalsWithLogin() throws Exception {
        mockMvc.perform(get("/api/animals/mine")).andExpect(status().isOk());
    }

    @Test
    void createRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/animals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "owner@email.com")
    void incompleteCreatePointsToFields() throws Exception {
        mockMvc.perform(post("/api/animals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.species").exists())
                .andExpect(jsonPath("$.errors.photos").value("Envie de 1 a 4 fotos"));
    }

    @Test
    @WithMockUser(username = "owner@email.com")
    void ageOutOfRangeForUnitReturns400() throws Exception {
        String body = """
                {"name":"Thor","species":"DOG","breed":"SRD","sex":"MALE",
                 "ageValue":14,"ageUnit":"MONTHS","size":"LARGE",
                 "energyLevel":"HIGH","temperament":"CALM","independenceLevel":"LOW","vocalization":"LOW",
                 "goodWithChildren":true,"goodWithDogs":true,"goodWithCats":true,"apartmentFriendly":true,
                 "summary":"Resumo","story":"História","photos":["https://fotos/1.jpg"]}
                """;

        mockMvc.perform(post("/api/animals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ageValid").exists());
    }

    @Test
    void changeStatusRequiresLogin() throws Exception {
        mockMvc.perform(patch("/api/animals/1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ADOPTED\"}"))
                .andExpect(status().isUnauthorized());
    }
}
