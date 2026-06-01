# services/

Microservices métier + front. Un dossier = un déployable indépendant (image Docker propre,
scalable séparément). Frontières détaillées dans `docs/architecture-decisions.md` §1.

| Dossier | Bounded context | Base |
|---|---|---|
| `identity-service/` | Identity & Access Management : authN, authZ/RBAC, JWT, CRUD users | PostgreSQL |
| `travel-service/` | Itinéraires : travels, destinations, activités, hébergements, transports | Neo4j |
| `payment-service/` | Paiement : méthodes, transactions, Stripe + PayPal | PostgreSQL |
| `admin-dashboard/` | Front d'administration responsive (Chrome/Firefox) | — |

Placeholder Phase 0 — aucun code applicatif.