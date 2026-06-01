# ansible/

Provisionnement de toute la stack. **Un rôle = une responsabilité** (pas de playbook
monolithique). Chaque rôle non trivial viendra avec son scénario Molecule prouvant
l'idempotence (un 2ᵉ run = 0 `changed`).

| Rôle | Responsabilité |
|---|---|
| `common/` | Base commune : paquets, utilisateurs, hardening minimal |
| `docker-host/` | Installe Docker + réseaux bridge (edge / backend / data) |
| `postgres/` | Conteneur PostgreSQL (identity, payment), comptes à droits limités |
| `neo4j/` | Conteneur Neo4j (travel) |
| `vault/` | Conteneur Vault (dev mode) + peuplement des secrets |
| `traefik/` | Gateway/edge : TLS, routage, LB, ForwardAuth |
| `observability/` | Loki + Promtail (optionnel selon RAM) |
| `app-deploy/` | Déploiement des services (paramétré par service + réplicas) |

> Placeholders Phase 0 — aucune tâche écrite. Détails dans `docs/architecture-decisions.md`.