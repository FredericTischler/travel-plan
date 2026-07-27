package com.travelplan.travel.entity;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.OffsetDateTime;
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
 */
@Node("Destination")
public class Destination {

    @Id
    private UUID id;

    private String name;

    private String country;

    private OffsetDateTime createdAt;

    private OffsetDateTime deletedAt;

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
}