# Travel-Plan

Environnement microservices + Admin Dashboard pour un système de gestion de voyages.

**Statut : Phase 0 (décisions d'architecture).** Aucune implémentation à ce stade.
Voir [`docs/architecture-decisions.md`](docs/architecture-decisions.md) — les choix y
sont tranchés (3 services métier, Postgres + Neo4j, Traefik gateway, Vault, Compose seul).

## Arborescence

```
services/        Microservices métier + front (Spring Boot / Angular)
ansible/roles/   Provisionnement par responsabilité (1 rôle = 1 brique)
ci/              Jenkins (build/test PR) + SonarQube (qualité), lancés à la demande
docs/            Sujet, grille d'audit, décisions d'architecture
```

## Stack (validée Phase 0)

- Services : Spring Boot · Dashboard : Angular
- Bases : PostgreSQL (identity, payment) · Neo4j (travel)
- Edge/Gateway/LB/TLS : Traefik
- Secrets : HashiCorp Vault (dev mode)
- Observabilité : Loki + Promtail (corrélation `X-Request-Id`)
- Orchestration : Docker Compose (pas de Kubernetes)
- Cible RAM : 8 Go → stack CI à la demande, 1 réplica par défaut