package com.travelplan.travel.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Neo4j node mapped to the {@code Destination} label.
 *
 * Increment 1 scope: a single node type, no relationships. {@code id} is
 * application-assigned (a plain UUID, no {@code @GeneratedValue} — Neo4j's
 * internal element id is a separate, opaque implementation detail we never
 * expose). Uniqueness of {@code id} is enforced by a Cypher constraint
 * created at startup, see {@link com.travelplan.travel.config.Neo4jSchemaInitializer}.
 *
 * Soft-delete pattern: nodes are never physically removed. The service sets
 * {@code deletedAt} to mark a destination as inactive. Queries filtering
 * active destinations always include {@code WHERE d.deletedAt IS NULL} — same
 * fondation as {@code User}/{@code Payment} in identity-service/payment-service.
 *
 * Increment 2 adds the outgoing {@code TRANSPORT} relationship — see
 * {@link #getTransports()} for why it is declarative-only.
 */
@Node("Destination")
public class Destination {

    @Id
    private UUID id;

    private String name;

    private String country;

    private OffsetDateTime createdAt;

    private OffsetDateTime deletedAt;

    /**
     * Outgoing {@code TRANSPORT} relationships to other destinations.
     *
     * Declarative mapping only: it documents the domain model, but increment
     * 2 deliberately does NOT read or write this relationship through Spring
     * Data Neo4j's standard load-modify-save aggregate flow. SDN persists a
     * {@code @Relationship} collection by deleting every existing
     * relationship of that type from the node and recreating it from the
     * in-memory list on every {@code save()}. Combined with the deliberate
     * "no anti-duplicate protection" decision for TRANSPORT (see
     * {@link com.travelplan.travel.repository.TransportRepository}), any
     * write path that loads a partial/lazily-fetched aggregate and saves it
     * back would silently wipe sibling relationships it never loaded. To
     * avoid that footgun, both creation and one-hop traversal go through
     * explicit Cypher in {@code TransportRepository} instead.
     */
    @Relationship(type = "TRANSPORT", direction = Relationship.Direction.OUTGOING)
    private List<Transport> transports = new ArrayList<>();

    protected Destination() {
        // required by Spring Data Neo4j
    }

    public Destination(String name, String country) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.country = country;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public List<Transport> getTransports() {
        return transports;
    }
}