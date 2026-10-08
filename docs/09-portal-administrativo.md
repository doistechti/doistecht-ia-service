# Portal Administrativo — Acesso com login e senha

> **Status:** em andamento. Nada commitado ainda (working tree da `main`).
> Implementação iniciada no Codex em 08/10/2026; este documento registra o escopo e o ponto de parada para continuar.

## Objetivo

Trocar o acesso ao portal administrativo (`demo/site/admin.html`), que hoje usa a chave fixa `IA_SERVICE_ADMIN_KEY`, por **login com e-mail e senha**, sessões com expiração e **troca obrigatória da senha no primeiro acesso**. A chave admin legada continua valendo para automações e para recuperação de emergência.

## Escopo

### Dentro
- Cadastro de administradores no banco (sem tela de cadastro: o primeiro é criado pela migration).
- Login, sessão, "quem sou eu", troca de senha e logout.
- Bloqueio do portal até a senha inicial ser trocada.
- Tela de login, tela de troca de senha e identificação do usuário logado no portal.
- Testes unitários e de integração do fluxo.

### Fora (por enquanto)
- Cadastro, edição e desativação de outros administradores pela interface.
- Recuperação de senha por e-mail.
- Perfis além de `ADMIN` (a constraint do banco só aceita `ADMIN`).
- Telas para as rotas de admin que já existem na API mas não estão no portal: editar cliente (`PATCH /v1/admin/clients/{id}`), rotacionar chave (`POST /v1/admin/clients/{id}/rotate-key`), templates (`/v1/admin/templates`) e uso (`UsageAdminController`).

## Desenho

### Banco — `V6__create_admin_access.sql`

| Tabela | Campos principais |
|---|---|
| `admin_user` | `email` (único), `password_hash`, `role` (`ADMIN`), `must_change_password`, `active` |
| `admin_session` | `admin_user_id`, `token_hash` (SHA-256, único), `expires_at` |

A migration insere o administrador inicial `doistechti@gmail.com` com `must_change_password = TRUE`. Só o hash da senha está no arquivo.

### Segurança
- **Senha:** PBKDF2-HMAC-SHA256, 210.000 iterações, salt de 16 bytes, formato `pbkdf2-sha256$iter$salt$hash`, comparação em tempo constante (`PasswordHasher`).
- **Token de sessão:** opaco, `dtia_admin_` + 32 bytes aleatórios em Base64 URL. O banco guarda só o SHA-256.
- **Validade:** `ia-service.admin-access.session-ttl` / `ADMIN_SESSION_TTL`, padrão `12h`.
- **Troca de senha:** de 12 a 128 caracteres, diferente da atual. Revoga todas as sessões do usuário e emite uma nova.
- **Front:** token no `sessionStorage` da aba e textos da API sempre inseridos via `textContent`.

### API — `AdminAuthController`

| Método | Rota | Autenticação | Resposta |
|---|---|---|---|
| `POST` | `/v1/admin/auth/login` | nenhuma | `{ token, expiresAt, user }` |
| `GET` | `/v1/admin/auth/me` | Bearer | `{ id, email, role, mustChangePassword }` |
| `POST` | `/v1/admin/auth/change-password` | Bearer | nova sessão `{ token, expiresAt, user }` |
| `POST` | `/v1/admin/auth/logout` | Bearer | `204` |

### Regras do `ApiKeyFilter` para `/v1/admin/**`

1. `POST /v1/admin/auth/login` → liberado.
2. Demais `/v1/admin/auth/*` → exige sessão válida (mesmo com troca de senha pendente); sem sessão → `401`.
3. Outras rotas admin:
   - `X-API-Key` igual à chave admin legada → liberado;
   - sem sessão válida → `401`;
   - sessão com `mustChangePassword` → `403`;
   - sessão válida → liberado.

Erros: credencial inválida → `401` (`AdminAuthenticationException`); senha nova inválida ou senha atual errada → `400` (`InvalidPasswordException`).

### Arquivos

| Tipo | Arquivo |
|---|---|
| Novo | `security/admin/` — `AdminAccessProperties`, `AdminAccessService`, `AdminAuthenticationException`, `AdminPrincipal`, `AdminSession`, `AdminSessionRepository`, `AdminUser`, `AdminUserRepository`, `InvalidPasswordException`, `PasswordHasher` |
| Novo | `api/controller/AdminAuthController.java` |
| Novo | `api/dto/AdminLoginRequest`, `AdminChangePasswordRequest`, `AdminSessionResponse` |
| Novo | `db/migration/V6__create_admin_access.sql` |
| Novo | `demo/site/admin-portal.js` (substitui o `admin.js` no `admin.html`) |
| Novo | `test/.../security/admin/PasswordHasherTest.java`, `AdminAccessIT.java` |
| Alterado | `security/ApiKeyFilter.java`, `exception/GlobalExceptionHandler.java`, `application.yml`, `demo/site/admin.html`, `ApiControllerTestSupport.java` |

## Checkpoint — onde paramos

### Feito
- [x] Migration V6 com tabelas e administrador inicial.
- [x] Hash de senha (PBKDF2) e serviço de sessão.
- [x] Endpoints de login, `me`, troca de senha e logout.
- [x] `ApiKeyFilter` aceitando Bearer, mantendo a chave legada e bloqueando o portal até a troca da senha.
- [x] Mapeamento dos novos erros no `GlobalExceptionHandler`.
- [x] Portal: login, troca obrigatória de senha, identificação do usuário e logout.
- [x] `PasswordHasherTest` e `AdminAccessIT` escritos.
- [x] Compilação OK e testes unitários passando (`./mvnw test`: 118 testes, 0 falhas, em 08/10/2026).

### Pendente
- [ ] **Senha inicial do `doistechti@gmail.com`:** recuperar no histórico do Codex. Sem ela não há primeiro login. Se não for possível recuperar, gerar um novo hash com o `PasswordHasher` e trocar na V6 (enquanto ela não tiver rodado em nenhum ambiente).
- [x] Rodar os testes de integração com o Docker ligado (`./mvnw verify`): 118 unitários + 58 de integração, incluindo o `AdminAccessIT`, todos passando em 08/10/2026.
- [ ] Testar o portal de ponta a ponta no navegador: login → troca de senha → lista/cria clientes → logout → sessão expirada.
- [x] Remover `demo/site/admin.js` (não é mais referenciado) e `patch-test.tmp` (sobra na raiz).
- [x] Tirar o e-mail fixo do `value` do campo de login em `admin.html`.
- [x] Restaurar acentos nas mensagens novas ("inválida", "Sessão", "obrigatória"…). A mensagem de API key de cliente mudou de "inválida" para "invalida" e é vista pelos consumidores da API.
- [x] Revisar o rodapé do `admin.html` e o README/guia da API (`08-guia-da-api.md`) com as novas rotas e o fluxo de acesso.
- [ ] Commit só dos arquivos desta feature. Deixar de fora `LightPlus/`, `LightPlusLog/`, `Supply/`, `Veritas-projeto-todo/`, `sdd-veritas/`, `garage-operacao-reversa/`, `docker-mongodb*.yml`, `doistech-hub-poc-react.zip` e `package-lock.json`.

### Pontos para decidir
- [ ] Proteção contra força bruta no login: limite de tentativas por IP ou por e-mail, ou bloqueio temporário.
- [ ] Limpeza de sessões expiradas: hoje só acontece quando alguém faz login. Pode virar um job agendado.
- [ ] Manter ou não a chave admin legada nas rotas do portal depois que o login estiver estável.
- [ ] Levar para o portal as rotas de admin que hoje só existem na API (editar cliente, rotacionar chave, templates, uso).
