# travel-service

**Context :** Itinéraires de voyage (Spring Boot · Neo4j).

Responsabilités :
- CRUD travels : destinations (1..n), activités, hébergements, transports.
- Modèle graphe : `(Travel)-[:HAS_DESTINATION]->(Destination)-[:HAS_ACTIVITY]->(Activity)`,
  `(Destination)-[:STAYS_AT]->(Accommodation)`, `(Destination)-[:TRANSPORT {mode}]->(Destination)`.
- Cascade intra-base : `DETACH DELETE` (nœud + relations).
- Endpoint interne `/internal/by-user/{id}` pour la purge cross-service (cf. §3).

> Neo4j justifié par la nature graphe du domaine (cf. `docs/architecture-decisions.md` §2). Placeholder Phase 0.