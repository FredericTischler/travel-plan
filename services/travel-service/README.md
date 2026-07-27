# travel-service

**Context:** Destinations (Spring Boot · Neo4j).

## Current scope

Basic CRUD skeleton for a single node type, `Destination`. No relationships,
no graph traversal.

- `POST /destinations` — create a new destination (`name`, `country`).
- `GET /destinations/{id}` — get an active destination by id (404 if absent
  or soft-deleted).
- `GET /destinations` — list all active destinations.
- `PUT /destinations/{id}` — replace the mutable fields (`name`, `country`)
  of an active destination (404 if absent or soft-deleted).
- `DELETE /destinations/{id}` — soft-delete an active destination (404 if
  absent or already soft-deleted).

`Destination.id` is application-assigned (a plain UUID), not Neo4j's
internal (opaque) element id.

## Soft-delete

Nodes are never physically removed. "Delete" sets `deletedAt` on the node to
the current timestamp; the node stays. Every read Cypher query
(`findActiveById`, `findAllActive`) systematically filters on
`d.deletedAt IS NULL`, so a soft-deleted destination is indistinguishable
from a non-existent one to API callers — same foundation as `User`/`Payment`
in identity-service/payment-service.

## Schema constraint at startup

There is no Flyway (or equivalent migration tool) for this service. Instead,
`Neo4jSchemaInitializer` runs an idempotent Cypher statement at every startup
(`CREATE CONSTRAINT ... IF NOT EXISTS`) to ensure `Destination.id` is unique.
This is a deliberate choice for a single node type with a single, simple
constraint; it should be reconsidered if the graph model grows (multiple
node types, relationships, ordered/versioned schema changes).

## Assumed debt: Neo4j Community Edition access control

Unlike identity-service and payment-service, which each connect to Postgres
with a dedicated application account with limited rights, this service
connects to Neo4j using the single administrative account (`neo4j`).
Neo4j Community Edition has no RBAC and no multi-tenant user support: there
is exactly one administrative account, and no way to provision a scoped,
per-service account. This is a known, documented limitation of the edition,
not an oversight — see the Javadoc on `Neo4jConnectionConfig` for the full
rationale.

## Configuration

All connection values are externalized via environment variables in
`application.yml` (`NEO4J_HOST`, `NEO4J_PORT`, `NEO4J_USERNAME`,
`NEO4J_PASSWORD`, `SERVER_PORT`). There is no `dbname` variable: Neo4j
Community Edition has a single default database. The service fails fast at
startup if any required variable is absent.

## Not yet implemented

- No relationships between nodes (e.g. no `Travel`, `Activity`,
  `Accommodation`, or `Transport` nodes, no graph traversal endpoints). The
  current increment covers only standalone `Destination` CRUD.
- No `/internal/*` cross-service endpoints.