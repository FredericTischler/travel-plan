# identity-service

**Context:** Identity and authentication (Spring Boot · PostgreSQL).

## Current scope

Basic CRUD skeleton for the `User` resource, plus password-based
authentication:

- `POST /users` — create a user (email + password). Rejects with 409 if the
  email is already active. The plaintext password is hashed with BCrypt
  before persistence; it never reaches the repository.
- `GET /users/{id}` — get an active user by id (404 if absent or
  soft-deleted).
- `GET /users` — list all active users.
- `DELETE /users/{id}` — soft-delete an active user (404 if absent or already
  soft-deleted).
- `POST /login` — verify email + password for an active user. Returns 200
  with the user's id, email, and a signed JWT on success; 401 with a generic
  message on any failure (unknown email and wrong password are
  indistinguishable to the caller, including in response timing).
- `GET /me` — resolve the active user identified by the
  `Authorization: Bearer <token>` header. Returns 401 (same generic message)
  if the header is absent/malformed, the token is expired/invalid, or its
  subject no longer maps to an active user.

There is no Spring Security filter chain in this codebase. Token validation
on `GET /me` is manual (read the header, verify via the JWT service). Every
other route, including `/login`, remains unprotected by any global filter.

## Soft-delete

Rows are never physically removed. "Delete" sets `deleted_at` to the current
timestamp; the row stays. Every read (`findById`, `findAll`) systematically
filters on `deleted_at IS NULL`, so a soft-deleted user is indistinguishable
from a non-existent one to API callers.

A soft-deleted user's email is not blocked for re-registration — the unique
constraint on `email` only applies among active (non-deleted) rows.

## Authentication and JWT

- Passwords are hashed with BCrypt (`BCryptPasswordEncoder`).
- On login, tokens are HS256-signed JWTs with a 15-minute expiration. There is
  no refresh token, no revocation/blacklist, and no roles/permissions carried
  in the token — out of scope for this increment.
- The token subject is the user id; the only custom claim is the email.

## Assumed debt

- `JWT_SIGNING_KEY` is read directly from a plain environment variable — it
  is **not** wired to Vault the way the `DB_*` credentials are (those are
  injected by Docker Compose from Vault). This service never talks to Vault
  directly (consistent with the "Ansible reads from Vault and renders" model
  used elsewhere in this repo); the JWT signing key is an explicit, assumed
  gap in that model, not a silent shortcut. See the Javadoc on `JwtService`
  for the full rationale.

## Configuration

All connection and secret values are externalized via environment variables
in `application.yml` (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SIGNING_KEY`, `SERVER_PORT`). The service fails fast at
startup if any of them is absent — no silent default for a secret.

Schema is owned by Flyway (`src/main/resources/db/migration/`); Hibernate
`ddl-auto` is set to `validate` only.