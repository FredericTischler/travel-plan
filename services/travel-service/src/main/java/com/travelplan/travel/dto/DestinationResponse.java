package com.travelplan.travel.dto;

import com.travelplan.travel.entity.Destination;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API response for a destination resource.
 *
 * Intentionally omits {@code deletedAt}: that field is an internal
 * soft-delete implementation detail and must never be exposed over the API.
 */
public class DestinationResponse {

    private final UUID id;
    private final String name;
    private final String country;
    private final OffsetDateTime createdAt;

    private DestinationResponse(UUID id, String name, String country, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.createdAt = createdAt;
    }

    public static DestinationResponse from(Destination destination) {
        return new DestinationResponse(
                destination.getId(),
                destination.getName(),
                destination.getCountry(),
                destination.getCreatedAt());
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}