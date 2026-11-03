# Integração com o código que já existe

Os módulos `payment/`, `auth/password/`, `chat/` e `common/` acessam o banco por JDBC e JPA próprios,
então **não dependem** das suas entidades. As alterações abaixo são nas classes que já existem no seu
projeto (que eu não tenho). Ajuste nomes e pacotes ao que você tem.

Pacote base usado: `com.aucolher`. Se o seu for outro, renomeie ao copiar.

## 0. Dependências (pom.xml)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```
Precisa de Spring Boot **3.2 ou superior** (o `MercadoPagoClient` usa `RestClient`) e Java 17.
Os demais starters (web, data-jpa, security, test) você já tem.

## 1. Banco

Rode, em ordem, `sql/migrations/009_...`, `010_...`, `011_...`. Para um banco novo, use `sql/schema.sql`.

## 2. Propriedades

Copie `src/main/resources/application-additions.properties` para o seu `application.properties`
e preencha o `.env` a partir de `.env.example`.

## 3. SecurityConfig

Rotas fixas antes das genéricas:

```java
.requestMatchers(HttpMethod.GET,  "/api/mercado-pago/oauth/callback").permitAll()
.requestMatchers(HttpMethod.POST, "/api/webhooks/mercadopago").permitAll()
.requestMatchers("/api/auth/**").permitAll()                    // já existe: cobre /api/auth/password/**
.requestMatchers("/api/ngo/**").hasRole("NGO")
.requestMatchers("/api/chats/**").hasAnyRole("PERSON", "NGO")   // o admin recebe 403
```
O CSRF deve estar desligado (API com JWT), senão o webhook (POST sem cookie) é bloqueado.

## 4. Derrubar sessões ao redefinir a senha (api-senha §5.1)

**JwtService**: já grava o `iat`; acrescente:
```java
public Date extractIssuedAt(String token) {
    return extractClaim(token, Claims::getIssuedAt);   // adapte à sua forma de ler claims
}
```

**Entidade User**: acrescente o campo mapeando a coluna nova.
```java
@Column(name = "password_changed_at")
private LocalDateTime passwordChangedAt;
// getter e setter
```

**CustomUserDetailsService**: devolva um `UserDetails` que leve o `passwordChangedAt`
(por exemplo, uma classe `AuthenticatedUser implements UserDetails` com esse campo).

**JwtAuthenticationFilter**: só autentique se o token for posterior à troca, comparando em **segundos**:
```java
LocalDateTime changedAt = authenticatedUser.getPasswordChangedAt();
boolean tokenIsFresh = changedAt == null
        || jwtService.extractIssuedAt(token).toInstant().getEpochSecond()
           >= changedAt.atZone(ZoneId.systemDefault()).toEpochSecond();
if (tokenIsFresh) { /* segue o fluxo normal de autenticação */ }
// token velho: a requisição segue sem autenticação -> rota protegida responde 401
```
O `PasswordResetService` já grava `password_changed_at` truncado para segundos, com o fuso da JVM.
Use o mesmo fuso na comparação.

**AuthService.authenticate**: hoje recusa qualquer conta com `provider != LOCAL`. Passe a recusar só
quando `password == null` (mesma mensagem: "Esta conta utiliza login via Google..."). Conta do Google
que criou senha entra pelos dois caminhos; o `provider` continua `GOOGLE`.

**UserResponseDTO**: acrescente `boolean hasPassword` (`password != null`) em todas as respostas que
trazem o usuário (login, cadastro, OAuth e `PUT /api/users/me`).

## 5. Chat: mensagens de sistema (api-chat §4.3)

**Feito.** O `AdoptionRequestService` tem o `ChatService` injetado e chama **dentro da mesma
`@Transactional`**, depois de aplicar a transição:

| Transição | Chamada |
|---|---|
| ACCEPTED → AWAITING_DELIVERY | `chatService.addSystemMessage(request.getId(), SystemEvent.DELIVERY_REQUESTED)` |
| AWAITING_DELIVERY → ACCEPTED (adotante: "Ainda não recebi") | `SystemEvent.DELIVERY_DECLINED` |
| PENDING → ACCEPTED | nenhuma |
| AWAITING_DELIVERY → CONCLUDED (dentro do `conclude(...)`) | `SystemEvent.ADOPTION_CONCLUDED` |
| CANCELLED pelo adotante, só se partiu de ACCEPTED ou AWAITING_DELIVERY | `SystemEvent.ADOPTER_WITHDREW` |
| CANCELLED pelo dono (ACCEPTED ou AWAITING_DELIVERY) | `SystemEvent.OWNER_WITHDREW` |
| `registerReturn(...)` (CONCLUDED → RETURNED) | `SystemEvent.ANIMAL_RETURNED` |
| `rehome(...)` (CONCLUDED → REHOMED) | `SystemEvent.ANIMAL_REHOMED` |

Cancelamentos em lote (`ANIMAL_ADOPTED`, `ANIMAL_UNAVAILABLE`, moderação) **não** chamam o chat.
Corrigir um retorno já registrado (RETURNED → RETURNED) também não: o aviso sai só no primeiro registro.

## 6. Módulo de campanhas e doações (outro contrato)

Este pacote entrega o que o contrato do Mercado Pago define. Para o módulo de campanhas use:

- `MercadoPagoOAuthService.isConnected(ngoId)` para calcular `acceptingDonations` (campanha ACTIVE e conta CONNECTED).
- Em `POST /api/campaigns` e `POST /api/campaigns/{id}/donations`: se `!isConnected(ngoId)`, responda 400.
- Para criar o PIX: `String token = oauthService.getValidAccessToken(ngoId);` e depois
  `mercadoPagoClient.createPixOrder(token, "donation-" + id, "donation-" + id, valor, emailDoDoador)`.
  Grave `orderId` em `donations.mp_order_id` e os dados do PIX no DTO da doação.
- No `GET /donations/{id}` em PENDING, consulte com `mercadoPagoClient.getOrderState(token, orderId)`
  e use `DonationGateway.approve/markExpired/markCancelled`.

## 7. Exclusão de conta

`mercado_pago_accounts`, `password_reset_tokens`, `chat_messages` e `chat_reads` têm `ON DELETE CASCADE`
para `users`/`adoption_requests`, então a exclusão da ONG apaga a linha do Mercado Pago sozinha.
Se a exclusão for lógica (anonimização), apague a linha de `mercado_pago_accounts` no `DeleteAccountService`.

A exclusão do AUcolher **é** lógica, então o cascade não dispara: o `AccountDeletionService` apaga os links de
senha e os `chat_reads` da conta. As `chat_messages` ficam, para a outra parte manter o histórico.

## 8. Pontos para conferir

- **Principal do Spring Security**: `CurrentUserProvider` assume que `authentication.getName()` é o e-mail.
  Se o seu principal expõe o id, altere só esse método.
- **Sandbox do Mercado Pago** (seção 6 do contrato): confirme o caminho dos campos da resposta da Orders API
  (`MercadoPagoClient.createPixOrder`) e os nomes dos status (`mapOrderState`).
- **RETURNED e REHOMED**: o contrato do chat lista só ACCEPTED, AWAITING_DELIVERY, CONCLUDED e cancelados
  depois do aceite como status com conversa, mas também manda gravar `ANIMAL_RETURNED`/`ANIMAL_REHOMED`
  na conversa (que acontecem depois de CONCLUDED). Mantive a conversa visível (somente leitura) nesses
  dois status; está em `ChatService.WITH_CONVERSATION` e na query de `ChatMessageRepository.summarize`.
