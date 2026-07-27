package com.travelplan.travel.entity;

import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Neo4j relationship properties mapped to the {@code TRANSPORT} relationship
 * type, declared as an outgoing relationship on {@link Destination}.
 *
 * Directed, non-symmetric: {@code A-[TRANSPORT]->B} does not imply
 * {@code B-[TRANSPORT]->A}. If both directions exist, they are two distinct
 * relationships.
 *
 * {@code mode} is a plain String constrained to a fixed set of values
 * (TRAIN, PLANE, BUS, CAR, BOAT), enforced by
 * {@code TransportService} — same "no extensible enum, just an
 * allowed-values check in the service" approach as {@code Payment.status} in
 * payment-service.
 *
 * {@code id} is the Neo4j-internal relationship element id — never
 * application-assigned (no {@code @GeneratedValue}: unlike {@link
 * Destination#getId()}, a relationship's technical id is provided by Neo4j
 * itself at creation time) and never exposed over the API.
 *
 * Declarative mapping only for increment 2 — see the Javadoc on
 * {@link Destination#getTransports()} for why reads/writes bypass the
 * standard Spring Data Neo4j aggregate save/load flow.
 */
@RelationshipProperties
public class Transport {

    @RelationshipId
    private Long id;

    private String mode;

    private int durationMinutes;

    @TargetNode
    private Destination destination;

    protected Transport() {
        // required by Spring Data Neo4j
    }

    public Transport(Destination destination, String mode, int durationMinutes) {
        this.destination = destination;
        this.mode = mode;
        this.durationMinutes = durationMinutes;
    }

    public Long getId() {
        return id;
    }

    public String getMode() {
        return mode;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Destination getDestination() {
        return destination;
    }
}