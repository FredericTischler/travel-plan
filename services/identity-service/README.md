# identity-service

**Context :** Identity & Access Management (Spring Boot · PostgreSQL).

Responsabilités :
- Authentification + émission/validation JWT.
- Autorisation RBAC (rôle ADMIN requis pour le dashboard) — moindre privilège.
- CRUD des utilisateurs gérés par l'admin.
- Cascades intra-base (Postgres `ON DELETE/UPDATE CASCADE`) : user → credentials/rôles/permissions.
- Endpoint ForwardAuth consommé par Traefik pour protéger toutes les APIs.

> Fusion auth + user assumée (cf. `docs/architecture-decisions.md` §1). Placeholder Phase 0.