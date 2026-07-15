# UniSwap

A peer-to-peer campus marketplace REST API, built with Spring Boot. Students can register with their campus email, list items for sale, browse and search listings, and manage their own inventory — all secured with JWT-based authentication.

## Tech Stack

- **Java 21**
- **Spring Boot 4.1.0**
- **Spring Data JPA** (Hibernate) — persistence
- **Spring Security** + **JWT** (jjwt 0.12.6) — stateless authentication
- **MySQL** — database
- **Lombok** — boilerplate reduction
- **Maven** — build tool

## Features

- Student registration and login with hashed passwords (BCrypt) and JWT issuance
- Create, browse, search, update, and delete product listings
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
- MySQL running locally

### Setup

1. Clone the repository
   ```bash
   git clone https://github.com/Damini310/UniSwap-Api.git
   cd UniSwap-Api
   ```

2. Configure your database credentials in `src/main/resources/application.yaml`:
   ```yaml
   spring:
     datasource:
       username: root
       password: <your-mysql-password>
   ```
   The database (`uniswap_db`) is created automatically on first run — no manual schema setup needed.

3. **Before deploying anywhere public**, replace the `jwt.secret` value in `application.yaml` with a securely generated secret, ideally injected via an environment variable rather than committed to source control.

4. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
   The API will be available at `http://localhost:8080`.

### Testing the API

A ready-to-import Postman collection is available covering the full flow (register → login → create/browse/update/delete listings), including a request that verifies unauthenticated writes are correctly rejected.

## API Reference

All request/response bodies are JSON. Endpoints marked 🔒 require an `Authorization: Bearer <token>` header.

### Auth

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account, returns a JWT + user info |
| POST | `/api/auth/login` | Authenticate, returns a JWT + user info |

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

## Security Notes

- Passwords are hashed with BCrypt before storage — never stored or returned in plaintext.
- JWTs are stateless (no server-side session storage), signed with HMAC-SHA256.
- Ownership of a listing is verified server-side against the authenticated JWT identity on every write operation — a client cannot claim another user's identity by modifying request data.
- API error responses never leak stack traces, SQL, or internal implementation details to the client.

## Roadmap

- [ ] Image upload for listings (currently a plain URL field)
- [ ] Pagination on listing endpoints
- [ ] Automated integration tests
- [ ] Optional: restrict registration to a specific campus email domain