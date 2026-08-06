# UniSwap

A peer-to-peer campus marketplace REST API, built with Spring Boot. Students can register with their campus email, list items for sale, browse and search listings, and manage their own inventory — all secured with JWT-based authentication.

## Tech Stack

- **Java 21**
- **Spring Boot 4.1.0**
- **Spring Data JPA** (Hibernate) — persistence
- **Spring Security** + **JWT** (jjwt 0.12.6) — stateless authentication
- **PostgreSQL** — database
- **Lombok** — boilerplate reduction
- **Maven** — build tool

## Features

- Student registration and login with hashed passwords (BCrypt) and JWT issuance — both **password login** and **passwordless email-code login**
- Google "Sign in with" OAuth2 (shown once `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` are set)
- Email verification on signup, one-time login codes, and password reset via transactional email (SMTP — Brevo/Resend)
- Unique usernames enforced server-side, with a live availability check in the register form
- Create, browse, search, update, and delete product listings
- Image upload for listings
- Category filtering and title search
- Ownership enforcement — only a listing's seller can edit, mark it sold, or delete it
- "My Listings" view for a logged-in user's own inventory
- Centralized, consistent JSON error responses for validation failures, auth errors, and unexpected exceptions

## Project Structure

```
com.olamide.UniSwap
├── Config          # Security setup: JWT filter, UserDetails adapter, password encoder
├── Controller       # REST endpoints (Auth, User, Product)
├── Dto             # Request/response payloads — never expose entities directly
├── Entity          # JPA entities (User, Product)
├── Exception       # Global exception handling, consistent error shape
├── Repository      # Spring Data JPA repositories
└── Service         # Business logic: auth, JWT, product CRUD, ownership checks
```

## Getting Started

### Prerequisites

- Java 21+
- Maven (or use the included `mvnw` wrapper)
- PostgreSQL 18+ running locally

### Setup

1. Clone the repository
   ```bash
   git clone https://github.com/Damini310/UniSwap-Api.git
   cd UniSwap-Api
   ```

2. Copy the environment template and fill in your values:
   ```bash
   cp .env.example .env
   ```
   Everything (database credentials, JWT secret, email SMTP, Google OAuth) is
   read from environment variables — see `.env.example` for every variable.
   Never commit a filled-in `.env` (it's gitignored).

3. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
   The API will be available at `http://localhost:8080`.

### Email (Brevo)

The backend sends signup/login/reset codes over SMTP. To turn it on:

1. Create an account at [brevo.com](https://www.brevo.com) (Resend works too).
2. Verify a **sender**: *Senders & IP → Senders → Add a sender*, and confirm
   the email Brevo sends to it.
3. Get your SMTP credentials from *SMTP & API → SMTP*.
4. Set these in your `.env`:
   ```
   MAIL_HOST=smtp-relay.brevo.com
   MAIL_PORT=587
   MAIL_USERNAME=<your brevo smtp login>
   MAIL_PASSWORD=<your brevo smtp key>
   MAIL_FROM=<the verified sender email>
   ```
   With `MAIL_HOST` unset, the backend prints codes to its log instead (dev mode).

### Google "Sign in with"

The button only appears when the backend reports Google is configured:

1. Go to [console.cloud.google.com](https://console.cloud.google.com) → create a project.
2. *APIs & Services → OAuth consent screen* → type **External**, fill in the app
   name + support email, add the `email`, `profile`, and `openid` scopes, and add
   yourself as a **Test user**.
3. *APIs & Services → Credentials → Create credentials → OAuth client ID* →
   type **Web application**.
4. Add the **Authorized redirect URI** exactly as your `OAUTH_REDIRECT_URI`
   (default `http://localhost:8080/login/oauth2/code/google`; use your real
   backend URL in production).
5. Copy the **Client ID** and **Client Secret** into `.env`:
   ```
   GOOGLE_CLIENT_ID=<client id>
   GOOGLE_CLIENT_SECRET=<client secret>
   OAUTH_REDIRECT_URI=http://localhost:8080/login/oauth2/code/google
   ```
   Until the consent screen is **published**, only your test users can sign in.

### Testing the API

A ready-to-import Postman collection is available covering the full flow (register → login → create/browse/update/delete listings), including a request that verifies unauthenticated writes are correctly rejected.

## API Reference

All request/response bodies are JSON. Endpoints marked 🔒 require an `Authorization: Bearer <token>` header.

### Auth

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account, emails a 6-digit signup code (no JWT yet) |
| POST | `/api/auth/verify-email` | Confirm the emailed signup code and unlock login |
| POST | `/api/auth/resend-verification-code` | Re-send the signup code for an unverified account |
| POST | `/api/auth/login` | Log in with email + password, returns a JWT + user info |
| POST | `/api/auth/login-code` | Passwordless login step 1: email a one-time code |
| POST | `/api/auth/login-code/verify` | Passwordless login step 2: exchange the code for a JWT |
| POST | `/api/auth/forgot-password` | Email a one-time password-reset code |
| POST | `/api/auth/reset-password` | Set a new password with the reset code |
| GET | `/api/auth/check-username` | Live "is this username free?" check for the register form |
| GET | `/api/auth/config` | Report OAuth/Google availability to the frontend |

### Users

| Method | Endpoint | Description |
|---|---|---|
| GET 🔒 | `/api/users/me` | Get the currently authenticated user's profile |
| GET 🔒 | `/api/users/{id}` | Get a user's public profile by ID |

### Products

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/products` | List all available products. Supports `?category=` and `?search=` query params |
| GET | `/api/products/{id}` | Get a single product by ID |
| GET 🔒 | `/api/products/my-listings` | Get the authenticated user's own listings (available + sold) |
| POST 🔒 | `/api/products` | Create a new listing |
| PUT 🔒 | `/api/products/{id}` | Update a listing (seller only) |
| PATCH 🔒 | `/api/products/{id}/sold` | Mark a listing as sold (seller only) |
| DELETE 🔒 | `/api/products/{id}` | Delete a listing (seller only) |

## Deployment (Docker)

A `Dockerfile` (multi-stage Maven build → JRE 21) and `docker-compose.yml`
ship the PostgreSQL DB + backend together. The frontend is a separate repo
(`uniswap-frontend`) referenced as a sibling directory (`../uniswap-frontend`).

Pre-deploy checklist:

1. Set real values in `.env` (see `.env.example`) — especially:
   - `JWT_SECRET` — a long random string (`openssl rand -base64 48`)
   - `DB_USERNAME` / `DB_PASSWORD`
   - `APP_BASE_URL`, `APP_FRONTEND_URL`, `APP_BACKEND_URL` — your public origins
   - `CORS_ALLOWED_ORIGINS` — your frontend origin(s)
   - `MAIL_*` (Brevo) and `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
   - `OAUTH_REDIRECT_URI` — add the same URL to your Google OAuth client
2. Build and run:
   ```bash
   docker compose up --build -d
   ```
   The API is on `:8080` and the DB on host `:5433` (so it never clashes with a
   local PostgreSQL on `:5432`). Uploaded images persist in a named volume.
3. Swap `DB_DDL_AUTO=update` for Flyway + `validate` before going to production.

## Security Notes

- Passwords are hashed with BCrypt before storage — never stored or returned in plaintext.
- JWTs are stateless (no server-side session storage), signed with HMAC-SHA256.
- Ownership of a listing is verified server-side against the authenticated JWT identity on every write operation — a client cannot claim another user's identity by modifying request data.
- API error responses never leak stack traces, SQL, or internal implementation details to the client.

## Roadmap

- [ ] Pagination is done; add sorting/filtering on price and date
- [ ] Flyway migrations + `ddl-auto: validate` for production
- [ ] Optional: restrict registration to a specific campus email domain