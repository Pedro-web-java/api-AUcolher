# Guia — usar o banco do Aiven (casa e escola)

O banco PostgreSQL do AUcolher está na nuvem (Aiven). Assim o computador de casa e o da
escola usam **o mesmo banco**, sem copiar dump nem rodar migrations de novo.

- **Serviço:** PostgreSQL 18 no Aiven, banco `defaultdb`, usuário `avnadmin`, SSL obrigatório.
- **Como a API escolhe o banco:** `application.properties` lê `DB_URL`, `DB_USERNAME` e
  `DB_PASSWORD`. Sem essas variáveis, a API usa o `aucolher_db` local de sempre.
- **Onde ficam os dados de conexão:** no arquivo `.env.aiven`, que o Git ignora.
  O modelo está em `.env.aiven.example`.
- **O script só conhece o que está no `.env.aiven`.** As variáveis da run configuration do
  IntelliJ não valem quando a API sobe pelo `run-aiven.ps1`. Por isso as credenciais do login
  com o Google (`GOOGLE_CLIENT_ID` e `GOOGLE_CLIENT_SECRET`) também vão no `.env.aiven`.

## Antes de ir para a escola (em casa)

1. Faça o commit e o push do que estiver pendente.
2. Tenha à mão a **senha do Aiven** (console do Aiven → seu serviço → *Connection information*).
   Guarde em um lugar só seu, como o gerenciador de senhas do celular. **Não** a coloque no GitHub:
   o repositório é público.

## Na escola, a primeira vez

### 1. Baixar o projeto
```powershell
git clone https://github.com/joaouggdmn/api-aucolher.git
cd api-aucolher
git checkout feature/banco-aiven     # ou main, se a branch já foi mergeada
```
Se o projeto já estiver na máquina, use `git pull` em vez do clone.

### 2. Testar se a rede da escola deixa chegar no Aiven
Faça isto **antes de qualquer coisa**. Esse comando já vem no Windows:
```powershell
Test-NetConnection pg-ac96582-jpuggdn-2f5b.i.aivencloud.com -Port 20175
```
- `TcpTestSucceeded : True` → a rede deixa passar, siga em frente.
- `TcpTestSucceeded : False` → o firewall da escola bloqueia a porta 20175. Não adianta
  mexer no projeto. Tente outra rede (como o hotspot do celular) ou use o banco local.

### 3. Criar o `.env.aiven`
Copie o modelo e coloque a senha:
```powershell
Copy-Item .env.aiven.example .env.aiven
notepad .env.aiven
```
Troque `COLE_A_SENHA_DO_AIVEN_AQUI` pela senha real e as duas linhas `GOOGLE_*` pelas
credenciais do Google (Google Cloud → *APIs e serviços* → *Credenciais*, ou a run configuration
do IntelliJ, em **Environment variables**). Salve e feche. Cada linha tem o formato
`NOME=valor`, sem aspas e sem espaços em volta do `=`.

### 4. Conferir o caminho do Java e do Maven
O `run-aiven.ps1` assume, nesta ordem:
```
JAVA_HOME = %USERPROFILE%\.jdks\ms-21.0.12
Maven     = C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd
```
Se na escola esses caminhos forem diferentes, edite as duas linhas no topo do script
(`$env:JAVA_HOME = ...` e `$mvn = ...`). O projeto precisa de Java 17 ou mais novo.
Para descobrir o que existe na máquina:
```powershell
Get-ChildItem "$env:USERPROFILE\.jdks"
Get-ChildItem "C:\Program Files\JetBrains" -Directory
```

### 5. Subir a API
```powershell
.\run-aiven.ps1
```
A API sobe na porta 8080. Para usar outra porta: `.\run-aiven.ps1 --server.port=8089`.
Deu certo quando o log mostrar `HikariPool-1 - Start completed` e `Started AucolherApiApplication`.

### 6. Subir o frontend
Do jeito de sempre (`npm run dev` na pasta do frontend). O frontend continua falando com a API
em `http://localhost:8080/api`; só o banco por trás mudou.

## Usando o IntelliJ em vez do script
Em *Run → Edit Configurations → sua configuração do Spring Boot → Environment variables*, cole:
```
DB_URL=jdbc:postgresql://pg-ac96582-jpuggdn-2f5b.i.aivencloud.com:20175/defaultdb?sslmode=require;DB_USERNAME=avnadmin;DB_PASSWORD=SUA_SENHA
```
Aqui as variáveis são separadas por `;`.

## No dia a dia

- **Os dados são os mesmos nas duas máquinas.** O que você cadastrar em casa aparece na escola,
  e vice-versa.
- **Mudou o banco (tabela ou coluna nova)?** Aplique o SQL **uma vez** no Aiven. Ele vale para as
  duas máquinas. Guarde o SQL em `sql/migrations/` como sempre.
  ```powershell
  # precisa do psql; no Windows ele fica em C:\Program Files\PostgreSQL\18\bin\psql.exe
  & "C:\Program Files\PostgreSQL\18\bin\psql.exe" "postgres://avnadmin:SENHA@pg-ac96582-jpuggdn-2f5b.i.aivencloud.com:20175/defaultdb?sslmode=require" -f sql\migrations\NOME.sql
  ```
- **Sem `psql` na máquina?** Use o *Query editor* do console do Aiven ou um cliente gráfico
  (DBeaver, pgAdmin) com os mesmos dados de conexão.
- **Teste que apaga ou recria dados?** Não rode contra o Aiven. Crie um banco temporário no
  Postgres local e aponte a API para ele (`--spring.datasource.url=...`).
- **Quer voltar ao banco local?** Suba a API sem o script, do jeito antigo.

## Se der erro

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `TcpTestSucceeded : False` | Firewall da escola | Outra rede (hotspot) ou banco local |
| `Connection timed out` ao subir a API | Mesmo bloqueio de rede | Idem |
| `password authentication failed` | Senha errada no `.env.aiven` | Copie a senha de novo do console do Aiven |
| `Arquivo ...\.env.aiven não encontrado` | Você ainda não criou o arquivo | Passo 3 |
| `JAVA_HOME ... not found` / `mvn.cmd` não encontrado | Caminhos diferentes na escola | Passo 4 |
| `Schema-validation: missing table/column` | O banco do Aiven está sem uma migration | Aplique o SQL que falta no Aiven |
| Google: `The OAuth client was not found` (`401 invalid_client`) | Falta `GOOGLE_CLIENT_ID` no `.env.aiven`, e a API mandou o valor de exemplo | Passo 3 e suba a API de novo |
| O serviço não responde de jeito nenhum | Serviço desligado | Veja o status no console do Aiven e ligue-o |

## Segurança

- O repositório do GitHub é **público**. Senha do banco nunca vai nele.
- O `.env.aiven` está no `.gitignore` de propósito. Não use `git add -f` nele.
- Se a senha vazar (por exemplo, em um commit), redefina-a no console do Aiven e atualize o
  `.env.aiven` das duas máquinas. Apagar o arquivo de um commit novo **não** basta: o histórico
  continua guardando a senha.
