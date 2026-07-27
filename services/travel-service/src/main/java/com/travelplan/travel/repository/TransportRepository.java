package com.travelplan.travel.repository;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data access for the {@code TRANSPORT} relationship.
 *
 * Deliberately bypasses the {@code Destination} aggregate's default Spring
 * Data Neo4j load-modify-save flow (see the Javadoc on
 * {@link com.travelplan.travel.entity.Destination#getTransports()}) in
 * favour of explicit Cypher via {@link Neo4jClient}. No business logic lives
 * here — only data access. Existence/soft-delete checks on the origin and
 * target destinations are the caller's ({@code TransportService}'s)
 * responsibility; this class only creates/reads the relationship itself.
 */
@Repository
public class TransportRepository {

    // Plain CREATE — no MERGE, no prior-existence check for an identical
    // trip. Creating the same A->B transport twice is an accepted, documented
    // gap for increment 2 (see task rationale), not an oversight. Both
    // endpoint ids are assumed already verified active by the caller.
    private static final String CREATE_QUERY = """
            MATCH (origin:Destination), (target:Destination)
            WHERE origin.id = $fromId AND target.id = $toId
            CREATE (origin)-[:TRANSPORT {mode: $mode, durationMinutes: $durationMinutes}]->(target)
            """;

    // First real traversal query of the project (Phase 0 justification test).
    // deletedAt IS NULL is filtered at BOTH hops — origin and target — so a
    // soft-deleted target disappears from the result even though its
    // relationship row still physically exists (soft-delete never issues a
    // DETACH DELETE).
    private static final String OUTGOING_ACTIVE_QUERY = """
            MATCH (origin:Destination)-[t:TRANSPORT]->(target:Destination)
            WHERE origin.id = $id AND origin.deletedAt IS NULL AND target.deletedAt IS NULL
            RETURN t.mode AS mode, t.durationMinutes AS durationMinutes,
                   target.id AS targetId, target.name AS targetName, target.country AS targetCountry
            """;

    private final Neo4jClient neo4jClient;

    public TransportRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * Create a directed {@code TRANSPORT} relationship from the destination
     * identified by {@code fromId} to the one identified by {@code toId}.
     * Assumes both already exist and are active (checked by the caller).
     */
    public void create(UUID fromId, UUID toId, String mode, int durationMinutes) {
        neo4jClient.query(CREATE_QUERY)
                .bindAll(Map.of(
                        "fromId", fromId.toString(),
                        "toId", toId.toString(),
                        "mode", mode,
                        "durationMinutes", durationMinutes))
                .run();
    }

    /**
     * One-hop traversal: destinations reachable from {@code id} via an
     * outgoing {@code TRANSPORT} relationship, filtered to active (non
     * soft-deleted) origin and target.
     */
    public List<TransportEdge> findActiveOutgoing(UUID id) {
        return neo4jClient.query(OUTGOING_ACTIVE_QUERY)
                .bindAll(Map.of("id", id.toString()))
                .fetchAs(TransportEdge.class)
                .mappedBy((typeSystem, record) -> new TransportEdge(
                        record.get("mode").asString(),
                        record.get("durationMinutes").asInt(),
                        UUID.fromString(record.get("targetId").asString()),
                        record.get("targetName").asString(),
                        record.get("targetCountry").asString()))
                .all()
                .stream()
                .toList();
    }
}