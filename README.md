# AUcolher API — Plataforma de Adoção de Animais

API REST em Spring Boot 3 / Java 17 do AUcolher: cadastro, login e autenticação (tradicional + OAuth2 Google) de dois perfis de usuário, **ONG** e **Usuário Comum**, edição do perfil ("Minha conta"), anúncios de animais para adoção, favoritos, **eventos** das ONGs (com confirmação de presença) e o **AUmatch** (quiz de estilo de vida e deck ordenado por compatibilidade).

## Stack

Java 17 · Spring Boot 3.3 · Spring Security · Spring Security OAuth2 Client · Spring Data JPA · PostgreSQL · Lombok · JJWT (tokens JWT)

## Estrutura do projeto

O código é organizado **por funcionalidade**: cada parte do sistema tem seu pacote com controller, service, repository, entidades e DTOs juntos. Uma funcionalidade nova (ex: `adoption/`) ganha o seu pacote no mesmo molde; o que é usado por várias delas fica em `shared/`.

```
src/main/java/com/aucolher/api/
├── AucolherApiApplication.java
├── auth/                                   # Cadastro e login
│   ├── AuthController.java
│   ├── AuthService.java                    # Regra de negócio e hashing BCrypt
│   ├── AdminAccountInitializer.java        # Cria a conta ADMIN ao subir (ADMIN_EMAIL / ADMIN_PASSWORD)
│   └── dto/                                # LoginDTO, NgoRegistrationDTO, PersonRegistrationDTO, AuthResponseDTO
├── user/                                   # Conta do usuário e perfil ("Minha conta")
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   ├── entity/                             # User, TeamMember, VisitingHour, UserType, AuthProvider
│   └── dto/                                # ProfileUpdateDTO, UserResponseDTO, TeamMemberDTO, VisitingHourDTO
├── animal/                                 # Anúncios de adoção
│   ├── AnimalController.java
│   ├── AnimalService.java                  # Regras: só o dono altera, ADOPTED é definitivo, inativo só o dono vê
│   ├── AnimalRepository.java
│   ├── AnimalSpecifications.java           # Monta o WHERE da listagem a partir dos filtros recebidos
│   ├── entity/                             # Animal + enums (Species, AnimalSize, Sex, Level, Temperament, AnimalStatus...)
│   └── dto/                                # AnimalRequestDTO, AnimalDetailDTO, AnimalSummaryDTO (card), AnimalFilterDTO...
├── favorite/                               # Animais favoritados por cada usuário
│   ├── FavoriteController.java
│   ├── FavoriteService.java
│   ├── FavoriteRepository.java
│   └── entity/Favorite.java
├── event/                                  # Eventos das ONGs e confirmação de presença
│   ├── EventController.java
│   ├── EventService.java                   # Regras: só a ONG dona altera, capacidade, evento passado não muda
│   ├── EventRepository.java / EventAttendanceRepository.java
│   ├── entity/                             # Event, EventAttendance + EventCategory, EventStatus
│   └── dto/                                # EventRequestDTO, EventResponseDTO, AttendeeDTO, NgoSummaryDTO...
├── match/                                  # AUmatch: quiz da pessoa e deck por compatibilidade
│   ├── MatchController.java
│   ├── MatchService.java                   # Perfil do quiz e deck (filtra, pontua e ordena)
│   ├── MatchScoreCalculator.java           # O algoritmo: 6 critérios que somam 100
│   ├── MatchProfileRepository.java
│   ├── entity/                             # MatchProfile + enums das respostas (Housing, TimeAway...)
│   └── dto/                                # MatchProfileRequestDTO, MatchProfileDTO, MatchCardDTO, MatchCriterionDTO
├── shared/                                 # Comum a todas as funcionalidades
│   ├── dto/PageDTO.java                    # Formato padrão das respostas paginadas
│   ├── exception/                          # Exceções (400, 401, 403, 404), @RestControllerAdvice e ErrorResponseDTO
│   └── validation/                         # Sanitizer (normalização nos records), PhotoUrl, @FoundedYear
├── config/SecurityConfig.java              # Regras do Spring Security, CSRF off, OAuth2 login
└── security/
    ├── JwtService.java                     # Geração/validação de token JWT
    ├── JwtAuthenticationFilter.java        # Filtro que autentica requisições via Bearer token
    ├── CustomUserDetailsService.java
    ├── CustomOAuth2UserService.java        # Cria o usuário automaticamente no 1º login Google
    └── OAuth2AuthenticationSuccessHandler.java  # Devolve JWT em JSON após login Google
sql/schema.sql                              # Script de criação do banco aucolher_db + tabelas
sql/migrations/                             # Atualizações para bancos criados por versões anteriores do script
src/test/java/...                           # Testes (JUnit 5 + Mockito + MockMvc)
```

## Como rodar

### 1. Banco de dados

```bash
psql -U postgres -h localhost -f sql/schema.sql
```

O script cria o banco `aucolher_db` (se ainda não existir) e as tabelas `users`, `ngo_team`, `ngo_visiting_hours`, `animals`, `animal_photos`, `favorites`, `events`, `event_attendances` e `match_profiles`. Todas usam `IF NOT EXISTS`, então rodar o script de novo não apaga nada. É uma cópia de `docs/script_banco_aucolher.sql` do frontend — mantenha os dois iguais. Rode o script antes de subir a API: o Hibernate (`ddl-auto=validate`) não cria o banco nem as tabelas, só confere se elas batem com as entidades.

#### Banco criado antes da padronização em inglês

Se o seu `aucolher_db` ainda tem as tabelas em português (`usuarios`, `animais`, `favoritos`...), **não** rode o `schema.sql`: aplique as migrações, em ordem, uma vez só. Elas renomeiam tabelas, colunas e constraints e traduzem os valores gravados (`ONG` → `NGO`, `CACHORRO` → `DOG`...) sem apagar nenhum dado — as contas e senhas continuam valendo.

```bash
psql -U postgres -h localhost -d aucolher_db -f sql/migrations/001_users_english.sql
psql -U postgres -h localhost -d aucolher_db -f sql/migrations/002_animals_english.sql
```

Cada migração roda numa transação: se algo falhar, o banco fica como estava. Sem elas a API não sobe (erro `Schema-validation: missing table [users]`).

#### Conta ADMIN e ONGs verificadas (migração 003)

Um banco criado antes do tipo `ADMIN` recusa a conta admin (a constraint `chk_users_user_type` só aceita `NGO` e `PERSON`). Aplique a 003 uma vez. Ela também marca como verificadas as ONGs que já existem, porque toda ONG nasce verificada:

```bash
psql -U postgres -h localhost -d aucolher_db -f sql/migrations/003_admin_and_verified_ngos.sql
```

Roda numa transação e pode ser repetida sem estragar nada. Banco novo, criado pelo `schema.sql` atual, não precisa dela.

#### Eventos (migração 004) e perfil AUmatch (migração 005)

Um banco criado antes dessas funcionalidades não tem as tabelas `events`, `event_attendances` e `match_profiles`, e a API não sobe sem elas (`Schema-validation: missing table [...]`). Aplique, em ordem, uma vez só:

```bash
psql -U postgres -h localhost -d aucolher_db -f sql/migrations/004_events.sql
psql -U postgres -h localhost -d aucolher_db -f sql/migrations/005_match_profiles.sql
```

Elas só criam tabelas novas (nenhum dado existente muda) e podem ser repetidas. Banco novo, criado pelo `schema.sql` atual, não precisa delas.

Depois de trazer as mudanças do Git, faça **Build → Rebuild Project** no IntelliJ (ou `mvn clean`): classes antigas compiladas (`Usuario`, `Animal` com campos em português) ficam na pasta `target/` e impedem a API de subir.

### 2. Variáveis de ambiente

| Variável              | Descrição                                   | Padrão (dev)          |
|-----------------------|----------------------------------------------|------------------------|
| `DB_USERNAME`         | Usuário do PostgreSQL                        | `postgres`             |
| `DB_PASSWORD`         | Senha do PostgreSQL                          | `1234`                  |
| `GOOGLE_CLIENT_ID`    | Client ID do Google OAuth2                   | —                       |
| `GOOGLE_CLIENT_SECRET`| Client Secret do Google OAuth2               | —                       |
| `JWT_SECRET`          | Chave HMAC dos tokens JWT (mínimo 32 caracteres) | — (sem padrão versionado) |
| `JWT_EXPIRATION_MS`   | Validade do token em milissegundos           | `86400000` (24h)        |
| `CORS_ALLOWED_ORIGINS`| Origens liberadas no CORS (separadas por vírgula) | `http://localhost:5173` |
| `ADMIN_EMAIL`         | E-mail da conta admin criada ao subir        | — (sem padrão versionado) |
| `ADMIN_PASSWORD`      | Senha da conta admin (mínimo 8 caracteres)   | — (sem padrão versionado) |
| `ADMIN_NAME`          | Nome exibido da conta admin                  | `Administrador`         |

Sem `JWT_SECRET`, a API sobe gerando uma chave aleatória por execução (registra um WARN no log): dá para desenvolver, mas todo restart invalida os tokens já emitidos. Defina a variável em qualquer ambiente que não seja a sua máquina.

**Conta admin.** Não existe cadastro público de admin: quem cria a conta `ADMIN` é a própria API, ao subir.
- Ela usa `ADMIN_EMAIL` e `ADMIN_PASSWORD` (mínimo 8 caracteres) e só cria a conta se esse e-mail ainda não existir.
- Se faltar alguma das duas variáveis, ou se a senha for curta, nenhuma conta é criada e o log registra um WARN com o motivo.
- Uma conta que já existe nunca é alterada:
  - trocar `ADMIN_PASSWORD` depois não muda a senha;
  - um e-mail que já pertence a uma pessoa ou ONG não vira admin.

No IntelliJ, defina as variáveis assim:
1. Abra **Run → Edit Configurations**.
2. Escolha a configuração da API.
3. Em **Environment variables**, coloque `ADMIN_EMAIL=admin@aucolher.com;ADMIN_PASSWORD=uma-senha-forte`.

Na primeira execução, o log mostra `Conta admin criada para ...`.

Para o login Google funcionar, crie as credenciais em [Google Cloud Console](https://console.cloud.google.com/apis/credentials) com **Authorized redirect URI**: `http://localhost:8080/login/oauth2/code/google`.

### 3. Executar

```bash
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`.

### 4. Testes

```bash
mvn test
```

Os testes não precisam de banco: as regras de negócio são testadas com o repositório simulado (Mockito) e as rotas com MockMvc.

## Endpoints

### Cadastro de ONG
```
POST /api/auth/register/ngo
Content-Type: application/json

{
  "name": "ONG Amigos dos Animais",
  "email": "amigosdosanimais@gmail.com",
  "password": "senhaSegura123",
  "cnpj": "12.345.678/0001-95",
  "cep": "88900-000",
  "street": "Rua Caetano Lummertz",
  "number": "1250",
  "complement": null,
  "district": "Centro",
  "city": "Araranguá",
  "state": "SC",
  "photoUrl": null,
  "bio": "Resgatamos e encontramos lares para cães e gatos.",
  "institutionalEmail": "contato@amigosdosanimais.org",
  "instagram": "amigosdosanimais",
  "twitter": "amigosanimais",
  "facebook": "https://facebook.com/amigosdosanimais",
  "foundedYear": 2016
}
```

A ONG já nasce ativa e com `isVerified: true`: não há etapa de aprovação. Endereço é obrigatório; `photoUrl`, `bio`, `institutionalEmail`, redes sociais e `foundedYear` são opcionais. `foundedYear` precisa estar entre 1800 e o ano atual. CNPJ e CEP podem vir com ou sem máscara (são gravados só com dígitos) e Instagram/X com ou sem `@`.

### Cadastro de Usuário Comum
```
POST /api/auth/register/person
Content-Type: application/json

{
  "name": "Maria Silva",
  "email": "maria@email.com",
  "password": "senhaSegura123",
  "photoUrl": null
}
```

### Login (qualquer tipo de conta)
```
POST /api/auth/login
Content-Type: application/json

{ "email": "maria@email.com", "password": "senhaSegura123" }
```

Rota única para qualquer tipo de conta: o tipo vem em `user.userType` na resposta (`NGO`, `PERSON` ou `ADMIN`).

### Formato dos erros

Todos os erros seguem o mesmo formato. `message` já vem pronta para exibir (em português); `errors` só aparece em falhas de validação, com o motivo de cada campo:

```json
{
  "timestamp": "2026-09-20T18:14:01.173",
  "status": 400,
  "error": "Dados inválidos",
  "message": "CNPJ inválido",
  "errors": { "cnpj": "CNPJ inválido", "cep": "CEP em formato inválido" }
}
```

Todas as respostas de autenticação seguem o formato:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "user": {
    "id": 1,
    "name": "Maria Silva",
    "email": "maria@email.com",
    "userType": "PERSON",
    "provider": "LOCAL",
    "createdAt": "2026-09-20T18:14:01.173",
    "photoUrl": null,
    "bio": null,
    "cnpj": null,
    "institutionalEmail": null,
    "isVerified": false,
    "instagram": null,
    "twitter": null,
    "facebook": null,
    "foundedYear": null,
    "team": [],
    "visitingHours": [],
    "cep": null,
    "street": null,
    "number": null,
    "complement": null,
    "district": null,
    "city": null,
    "state": null
  }
}
```

Use o token nas requisições autenticadas: `Authorization: Bearer {token}`.

### Edição do perfil ("Minha conta")
```
PUT /api/users/me
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "ONG Amigos dos Animais",
  "photoUrl": null,
  "bio": "Resgatamos e encontramos lares para cães e gatos.",
  "institutionalEmail": "contato@amigosdosanimais.org",
  "instagram": "amigosdosanimais",
  "twitter": null,
  "facebook": "https://facebook.com/amigosdosanimais",
  "foundedYear": 2016,
  "team": [{ "name": "Marina Costa", "role": "Presidente" }],
  "visitingHours": [{ "days": "Terça a sexta", "hours": "14h às 18h" }],
  "cep": "88900-000",
  "street": "Rua Caetano Lummertz",
  "number": "1250",
  "complement": "Galpão B",
  "district": "Centro",
  "city": "Araranguá",
  "state": "SC"
}
```

A conta editada é sempre a do token — não há id na rota. É uma substituição completa: campo opcional que não vier é apagado, e `team`/`visitingHours` substituem as listas inteiras (na ordem enviada). Usuário comum grava só `name`, `photoUrl`, `bio`, `cep`, `city` e `state`; o resto é ignorado. Para ONG o endereço continua obrigatório. E-mail, senha e CNPJ não mudam por aqui. Responde com o mesmo objeto `user` das rotas de autenticação — que também traz `team` e `visitingHours`, para o frontend não depender de nada guardado no navegador.

### Perfil público
```
GET /api/users/{id}/public-profile
```

Público, sem login: é a página de perfil de uma ONG ou pessoa. Traz os mesmos campos do objeto `user` acima, menos `email` e `provider`.
- ONG: perfil institucional completo, com endereço da sede, equipe e horários de visita.
- Pessoa: só `city` e `state` do endereço; CEP, rua, número e os campos de ONG vêm nulos.
- Conta inexistente, desativada ou admin responde 404.

### Login via Google (OAuth2)
```
GET /oauth2/authorization/google
```
Redirecione o usuário para essa URL no navegador. Após o consentimento, o Spring Security completa o fluxo OAuth2; no primeiro acesso, `CustomOAuth2UserService` cria automaticamente o registro em `users` com `userType = PERSON` e `provider = GOOGLE`. Em seguida, `OAuth2AuthenticationSuccessHandler` devolve o mesmo formato de JSON (token + usuário) dos logins tradicionais.

### Animais

Qualquer conta (ONG ou usuário comum) pode anunciar. A cidade/UF do animal são as do perfil de quem anuncia — preencha-as em `PUT /api/users/me` antes, senão o cadastro responde 400.

| Método e rota | Login | O que faz |
|---|---|---|
| `GET /api/animals` | não | Vitrine: só os `AVAILABLE`, do mais recente para o mais antigo, com filtros e paginação |
| `GET /api/animals/{id}` | não | Detalhes completos. Anúncio `INACTIVE` só aparece para o dono (para os outros, 404) |
| `POST /api/animals` | sim | Cadastra — responde 201 com o animal completo |
| `PUT /api/animals/{id}` | dono | Edição completa (mesmo corpo do cadastro). Animal adotado não pode ser editado |
| `PATCH /api/animals/{id}/status` | dono | `{ "status": "ADOPTED" }`, `"INACTIVE"` ou `"AVAILABLE"`. `ADOPTED` é definitivo |
| `DELETE /api/animals/{id}` | dono | Tira do ar (exclusão lógica: vira `INACTIVE`) — responde 204 |
| `GET /api/animals/mine` | sim | Todos os anúncios da conta logada, em qualquer status |
| `GET /api/users/{id}/animals` | não | Os disponíveis de um perfil público |

Quem não é dono recebe 403 nas rotas de alteração.

**Cadastro / edição** (`POST` e `PUT`):
```json
{
  "name": "Thor",
  "species": "DOG",
  "breed": "Vira-lata",
  "sex": "MALE",
  "ageValue": 3,
  "ageUnit": "YEARS",
  "size": "LARGE",
  "vaccinated": true,
  "neutered": true,
  "dewormed": true,
  "specialNeeds": false,
  "energyLevel": "HIGH",
  "temperament": "PROTECTIVE",
  "independenceLevel": "MODERATE",
  "vocalization": "MODERATE",
  "goodWithChildren": true,
  "goodWithDogs": true,
  "goodWithCats": false,
  "apartmentFriendly": false,
  "summary": "Protetor e brincalhão, pronto para uma nova aventura!",
  "story": "Thor foi resgatado ainda filhote...",
  "photos": ["data:image/jpeg;base64,...", "https://..."]
}
```

Valores aceitos:

| Campo | Valores |
|---|---|
| `species` | `DOG`, `CAT`, `OTHER` |
| `sex` | `MALE`, `FEMALE` |
| `size` | `SMALL`, `MEDIUM`, `LARGE` |
| `ageUnit` | `MONTHS` (0 a 11) ou `YEARS` (1 a 30) |
| `energyLevel`, `independenceLevel`, `vocalization` | `LOW`, `MODERATE`, `HIGH` |
| `temperament` | `CALM`, `PLAYFUL`, `AFFECTIONATE`, `PROTECTIVE`, `INDEPENDENT` |
| `photos` | de 1 a 4, URL http(s) ou data URL de imagem (mesmas regras da foto de perfil); a primeira é a capa |

Os itens de saúde (`vaccinated`, `neutered`, `dewormed`, `specialNeeds`) são opcionais e valem `false` quando não vêm. `ageGroup` (`PUPPY`, `ADULT`, `SENIOR`) não é enviado: a API calcula pela idade (menos de 1 ano é filhote; a partir de 8 anos, idoso).

A resposta do cadastro, da edição e de `GET /api/animals/{id}` traz esses campos mais `id`, `ageGroup`, `status`, `city`, `state`, `owner` (`id`, `name`, `photoUrl`, `userType`, `isVerified`), `createdAt` e `updatedAt`.

**Listagem** — filtros opcionais na query string. Os de múltipla escolha têm nome no plural e podem se repetir:
```
GET /api/animals?species=DOG&species=CAT&sizes=SMALL&city=Araranguá&page=0&size=12
```

Filtros: `search` (nome, raça ou cidade), `species`, `sizes`, `sexes`, `ageGroups`, `energyLevels`, `temperaments`, `specialNeeds=true`, `city`, `state`. Paginação: `page` começa em 0; `size` padrão 12, máximo 50. Atenção: `size` é o tamanho da página — o filtro de porte é `sizes`.

```json
{
  "content": [ { "id": 1, "name": "Thor", "coverPhoto": "data:image/jpeg;base64,...", "city": "Araranguá", "...": "..." } ],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1
}
```

Os cards (listagem, meus animais, perfil e favoritos) trazem só a `coverPhoto` e não trazem a `story` — a página de detalhes busca o animal completo.

### Favoritos

Todas exigem login.

| Método e rota | O que faz |
|---|---|
| `GET /api/favorites` | Cards dos animais favoritados, do mais recente para o mais antigo. Inativos somem; adotados continuam, com `status: ADOPTED` |
| `GET /api/favorites/ids` | Só os ids — para marcar os corações nos cards |
| `PUT /api/favorites/{animalId}` | Favorita. Repetir não duplica — responde 204 |
| `DELETE /api/favorites/{animalId}` | Desfavorita — responde 204 |

### Eventos

| Método e rota | Quem acessa | O que faz |
|---|---|---|
| `GET /api/events?ngoId=` | Público | Eventos ativos de hoje em diante; `ngoId` filtra por ONG |
| `GET /api/events/{id}` | Público | Detalhe; evento passado abre normalmente |
| `GET /api/events/mine` | ONG | Eventos da ONG logada, inclusive os passados |
| `GET /api/events/attending` | Logado | Eventos em que a pessoa confirmou presença (passados e cancelados inclusos) |
| `POST /api/events` | ONG | Cria o evento — 201 |
| `PUT /api/events/{id}` | ONG dona | Substitui todos os campos |
| `DELETE /api/events/{id}` | ONG dona | Exclui; com confirmados vira `CANCELLED` — 204 |
| `GET /api/events/{id}/attendees` | ONG dona | Lista de confirmados |
| `POST /api/events/{id}/attendance` | Logado | Confirma presença (idempotente) e devolve o evento |
| `DELETE /api/events/{id}/attendance` | Logado | Cancela a presença (idempotente) |

Categorias: `ADOPTION_FAIR`, `HEALTH`, `BAZAAR`, `WORKSHOP`. A data não pode estar no passado, o término precisa ser depois do início, e a ONG não confirma presença no próprio evento.

### AUmatch

Só pessoa física (`PERSON`), que é quem adota: sem token responde 401, e ONG ou admin recebem 403.

| Método e rota | O que faz |
|---|---|
| `GET /api/match/profile` | Respostas do quiz. Quem ainda não respondeu recebe tudo `null` e `complete: false` |
| `PUT /api/match/profile` | Grava as respostas. Substituição completa: pergunta que não vier fica sem resposta |
| `GET /api/match/deck` | Animais disponíveis, do maior para o menor score, com os pontos de cada critério |

**Perfil** (corpo do `PUT` e resposta do `GET`):
```json
{
  "housing": "APARTMENT",
  "exerciseRoutine": "ACTIVE",
  "timeAway": "REGULAR",
  "hasChildren": true,
  "hasDogs": false,
  "hasCats": true,
  "speciesPreference": "DOG",
  "idealPetProfile": "CALM_COMPANION",
  "preferredSize": "SMALL"
}
```

A resposta traz também `complete`: `true` quando as 7 perguntas têm resposta.
- Todos os campos são opcionais, porque "Minha conta" salva respostas soltas.
- `hasChildren`, `hasDogs` e `hasCats` respondem juntos a "Quem mora com você?": vêm os três ou nenhum, senão a API responde 400 com `errors.householdValid`.
- "Ninguém em casa" = os três `false`.

| Campo | Valores |
|---|---|
| `housing` | `APARTMENT`, `HOUSE_WITH_YARD`, `FARM` |
| `exerciseRoutine` | `SEDENTARY`, `ACTIVE`, `VERY_ACTIVE` |
| `timeAway` | `SHORT` (até 4h), `REGULAR` (4h a 8h), `LONG` (mais de 8h) |
| `speciesPreference` | `DOG`, `CAT`, `BOTH` |
| `idealPetProfile` | `CALM_COMPANION`, `PLAYFUL_ACTIVE`, `PROTECTIVE_INDEPENDENT` |
| `preferredSize` | `SMALL`, `MEDIUM`, `LARGE` |

**Deck** — lista de cards, sem paginação:
```json
[
  {
    "animal": { "id": 12, "name": "Thor", "coverPhoto": "...", "independenceLevel": "HIGH", "...": "..." },
    "matchScore": 85,
    "criteria": [
      { "key": "HOUSING", "points": 15, "maxPoints": 15, "answered": true },
      { "key": "ENERGY", "points": 8, "maxPoints": 20, "answered": true }
    ]
  }
]
```

- Entram os animais `AVAILABLE` de contas ativas, menos os da própria pessoa.
- `speciesPreference` `DOG` ou `CAT` deixa só essa espécie; `BOTH` ou sem resposta mostra todas.
- No empate de score, o anúncio mais recente vem primeiro.
- `animal` é o mesmo card da listagem de animais.

**O algoritmo** (`MatchScoreCalculator`) soma seis critérios, num total de 100:

| Critério (`key`) | Máx | Regra |
|---|---|---|
| `HOUSING` | 15 | Apartamento: 15 se o animal é `apartmentFriendly`, senão 0. Casa, sítio ou sem resposta: 15 |
| `ENERGY` | 20 | A rotina vira um nível (`SEDENTARY` LOW, `ACTIVE` MODERATE, `VERY_ACTIVE` HIGH), comparado com `energyLevel` |
| `INDEPENDENCE` | 20 | O tempo fora vira um nível (`SHORT` LOW, `REGULAR` MODERATE, `LONG` HIGH), comparado com `independenceLevel` |
| `LIVING` | 15 | Cada grupo em casa (crianças, cães, gatos) precisa do `goodWith...` correspondente: 15 × compatíveis ÷ grupos. Ninguém ou sem resposta: 15 |
| `TEMPERAMENT` | 15 | `CALM_COMPANION` aceita CALM ou AFFECTIONATE; `PLAYFUL_ACTIVE`, PLAYFUL; `PROTECTIVE_INDEPENDENT`, PROTECTIVE ou INDEPENDENT |
| `SIZE` | 15 | `preferredSize` comparado com `size`. Sem porte preferido, quem mora em apartamento conta como `SMALL` |

- Nas escalas (energia, independência e porte), igual vale o máximo, vizinho vale 40% (8 ou 6 pontos) e oposto vale 0.
- Os demais critérios sem resposta valem 0, então quem ainda não fez o quiz começa com 30.
- O frontend tem uma cópia do cálculo em `features/aumatch/utils/matchScore.js`: ao mudar uma regra, mude as duas.

## Decisões de projeto

- **Código em inglês, textos em português**: classes, campos, JSON, rotas, valores de enum, tabelas e colunas usam inglês, o padrão de mercado. Mensagens de erro (lidas pelo usuário final), comentários e documentação ficam em português. Termos brasileiros sem tradução exata (`cnpj`, `cep`) mantêm o nome.
- **JWT stateless**: como o requisito pede desativação de CSRF e uma API REST pura, optei por sessão stateless com JWT em vez de sessão HTTP tradicional — é o padrão de mercado para esse tipo de API e evita problemas de CORS/cookies com clientes SPA/mobile.
- **Senha nula para contas OAuth2**: usuários criados via Google não têm senha própria; o login tradicional (`/login`) rejeita essas contas com uma mensagem explicativa.
- **Normalização nos DTOs**: os construtores compactos dos records limpam os campos (trim, vazio vira null, CNPJ/CEP só com dígitos, rede social sem `@`, link com protocolo) antes da validação. A Service recebe dados já canônicos e cuida só da regra de negócio.
- **CNPJ pelo Bean Validation**: `@CNPJ` do Hibernate Validator confere os dígitos verificadores; um `@Pattern` extra barra sequências repetidas (`00.000.000/0000-00`), que o `@CNPJ` sozinho aceita.
- **Admin sem cadastro público**:
  - a conta `ADMIN` nasce das variáveis de ambiente (`AdminAccountInitializer`), então nenhuma senha fica no repositório;
  - `/api/admin/**` exige `ROLE_ADMIN`: sem token responde 401, e com token de pessoa ou ONG responde 403;
  - o repasse interno para `/error` é liberado (`DispatcherType.ERROR`). Sem isso, todo 403 da camada de segurança chegava ao cliente como 401, e o frontend desloga no 401.
- **JWT mínimo**: o token carrega apenas o e-mail (subject) e a validade. Papel e id não viram claim para não circular desatualizados — quem responde por eles é o banco, relido a cada requisição autenticada.
- **`ddl-auto=validate`**: o schema vem do `sql/schema.sql`; o Hibernate só confere se o banco bate com as entidades, em vez de alterar tabelas sozinho. Mudanças em bancos já existentes vão em `sql/migrations/`, numeradas e transacionais.
- **Constraints de banco** (`CHECK`) reforçam no schema.sql as mesmas regras já validadas na aplicação (ONG exige CNPJ; contas LOCAL exigem senha; valores dos enums dos animais) como camada extra de integridade.
- **Localização do animal = a do dono**: `animals` não tem cidade/UF; a listagem filtra pelo endereço do perfil de quem anunciou. Filtro por distância ("perto de mim") fica para quando `users` tiver latitude/longitude.
- **Exclusão lógica de animais**: `DELETE` só muda o status para `INACTIVE`, preservando o histórico (favoritos e, no futuro, pedidos de adoção).
- **Cards leves**: a listagem busca só a foto de capa (numa consulta única) e carrega o dono junto, então uma página custa duas consultas SQL, com payload pequeno mesmo com fotos em data URL.
- **Favoritar é idempotente no banco**: `INSERT ... ON CONFLICT DO NOTHING` garante um favorito por par usuário/animal mesmo com cliques simultâneos.
- **Score do AUmatch no backend**: o algoritmo roda na API (`MatchScoreCalculator`, coberto por testes) e o deck já chega ordenado. Como o score depende de cada animal, não dá para ordenar no banco: o deck carrega os candidatos e ordena em memória, sem paginação, o que basta para o volume do projeto.
- **Perfil AUmatch em tabela própria** (`match_profiles`, 1:1 com `users`): o quiz é só de pessoa física e pode mudar sem mexer no cadastro.
