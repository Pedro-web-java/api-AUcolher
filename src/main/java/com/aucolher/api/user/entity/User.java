package com.aucolher.api.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade que representa ONGs, usuários comuns e o admin.
 * O campo `userType` define o perfil, e `provider` define a
 * origem da autenticação (cadastro tradicional ou OAuth2/Google).
 *
 * Campos de perfil seguem a seção 6 de docs/regras-de-negocio.md:
 * bio e foto valem para os dois perfis; CNPJ, e-mail institucional,
 * redes sociais, equipe, horário de visitas e selo são do perfil de ONG.
 * O endereço é obrigatório para ONG e opcional para o usuário comum.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Hash BCrypt da senha. Fica nulo para usuários criados
     * automaticamente via login OAuth2 (Google), pois esses
     * usuários nunca informam senha própria.
     */
    @Column(length = 255)
    private String password;

    /**
     * Obrigatório apenas para userType = NGO.
     * Armazenado apenas com dígitos (sem máscara).
     */
    @Column(unique = true, length = 14)
    private String cnpj;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ===================== Perfil (comum e ONG) =====================

    /** URL do avatar (ou data URL da imagem comprimida, enquanto não há upload próprio). */
    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // ===================== Perfil de ONG =====================

    /** Contato público da ONG — pode ser diferente do e-mail de login. */
    @Column(name = "institutional_email", length = 150)
    private String institutionalEmail;

    /** Selo de "verificada": toda ONG nasce com ele (não há etapa de aprovação). */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean verified = false;

    /** Apenas o nome de usuário, sem @. */
    @Column(length = 30)
    private String instagram;

    /** Apenas o nome de usuário do X/Twitter, sem @. */
    @Column(length = 15)
    private String twitter;

    /** Link completo da página. */
    @Column(length = 255)
    private String facebook;

    /** Opcional — exibido no perfil como "Fundada em [ano]". Sempre nulo para usuário comum. */
    @Column(name = "founded_year")
    private Integer foundedYear;

    @ElementCollection
    @CollectionTable(name = "ngo_team", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<TeamMember> team = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "ngo_visiting_hours", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<VisitingHour> visitingHours = new ArrayList<>();

    // ===================== Endereço =====================

    /** Apenas dígitos (8). */
    @Column(length = 8)
    private String cep;

    @Column(length = 150)
    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 100)
    private String complement;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String city;

    /** Sigla da UF, ex: "SC". */
    @Column(length = 2)
    private String state;

    @PrePersist
    protected void onPersist() {
        this.createdAt = LocalDateTime.now();
    }
}
