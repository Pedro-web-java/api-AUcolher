# AUcolher — backend (módulos novos)

Módulos Spring Boot que implementam os contratos de API:

| Contrato | Pacote | Rotas |
|---|---|---|
| Conta Mercado Pago da ONG (migração 009) | `payment/` | `POST/GET/DELETE /api/ngo/mercado-pago`, `GET /api/mercado-pago/oauth/callback`, `POST /api/webhooks/mercadopago` |
| Recuperar e alterar senha (migração 010) | `auth/password/` | `POST /api/auth/password/forgot`, `/reset/check`, `/reset` |
| Chat dos pedidos de adoção (migração 011) | `chat/` | `GET /api/chats/summary`, `GET/POST /api/chats/{id}/messages`, `PUT /api/chats/{id}/read` |

`common/` tem o `ApiException` (erro no formato `{status, error, message}`), consultas mínimas a `users` e `donations`
e o `CurrentUserProvider`.

## Estrutura

```
sql/schema.sql                      banco completo (já com as migrações 009 a 011)
sql/migrations/009..011_*.sql       para quem já tem o banco criado
src/main/java/com/aucolher/...      código
src/test/java/com/aucolher/...      testes unitários (JUnit 5 + Mockito)
docs/INTEGRACAO.md                  o que alterar nas classes que já existem
.env.example                        variáveis de ambiente
```

## Como usar

1. Copie `src/main/java/com/aucolher/{common,payment,chat,auth}` e os testes para o seu projeto.
2. Siga `docs/INTEGRACAO.md` (dependência de e-mail, SecurityConfig, filtro JWT, hooks do chat).
3. Rode as migrações, copie as propriedades e preencha o `.env`.
4. `./mvnw test`

Chave de criptografia dos tokens: `openssl rand -base64 32` (vai em `MP_TOKEN_ENCRYPTION_KEY`).

## Não incluído

- Endpoints de campanhas e de criação/consulta de doações (outro contrato, `api-campanhas-doacoes.md`).
  O módulo `payment/` já expõe `getValidAccessToken`, `isConnected`, `createPixOrder` e `getOrderState` para eles.
- O restante da aplicação (usuários, animais, pedidos de adoção, JWT), que já existe no seu projeto.

## Subir no GitHub

```bash
git init
git add .
git commit -m "feat: conta Mercado Pago, recuperação de senha e chat"
git branch -M main
git remote add origin https://github.com/SEU-USUARIO/SEU-REPO.git
git push -u origin main
```
