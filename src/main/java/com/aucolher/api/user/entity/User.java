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
 * bio e foto valem para os dois perfis; CNPJ, redes sociais, equipe, horário de visitas e selo são do perfil de ONG.
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

    /** Última edição feita pelo admin (nula se nunca houve): o dono vê "Editado pela moderação". */
    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    /** O admin que fez essa edição (só o id, como nas denúncias). */
    @Column(name = "moderated_by")
    private Long moderatedBy;

    /** A própria pessoa excluiu a conta (nula se não): ela foi anonimizada e nunca é reativada. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ===================== Perfil (comum e ONG) =====================

    /** URL do avatar (ou data URL da imagem comprimida, enquanto não há upload próprio). */
    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    /** Banner do perfil público — só ONG (constraint chk_users_cover_only_ngo). */
    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // ===================== Perfil de ONG =====================

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

    /** O admin editou o conteúdo de outra pessoa: fica registrado quando e quem. */
    public void markModeratedBy(Long adminId) {
        moderatedAt = LocalDateTime.now();
        moderatedBy = adminId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Exclusão da conta: a linha fica (o histórico das outras pessoas aponta
     * para ela), mas sem nenhum dado pessoal. O e-mail e o CNPJ ficam livres
     * para um novo cadastro; o domínio .invalid nunca recebe correio.
     */
    public void anonymize(LocalDateTime now) {
        name = "Conta excluída";
        email = "excluida-" + id + "@aucolher.invalid";
        password = null;
        cnpj = null;
        photoUrl = null;
        coverUrl = null;
        bio = null;
        instagram = null;
        twitter = null;
        facebook = null;
        foundedYear = null;
        team.clear();
        visitingHours.clear();
        cep = null;
        street = null;
        number = null;
        complement = null;
        district = null;
        city = null;
        state = null;
        verified = false;
        active = false;
        deletedAt = now;
    }
}
