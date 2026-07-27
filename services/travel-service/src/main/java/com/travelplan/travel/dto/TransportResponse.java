package com.travelplan.travel.dto;

import java.util.UUID;

/**
 * API response for one TRANSPORT hop: the relationship's own properties
 * (mode, durationMinutes) plus the reachable target destination's public
 * fields — never the raw {@code Destination} entity, never {@code deletedAt}.
 *
 * Used both as the response of {@code POST /destinations/{fromId}/transports}
 * (the single created hop) and as each element of the list returned by
 * {@code GET /destinations/{id}/transports}.
 */
public class TransportResponse {

    private final String mode;
    private final int durationMinutes;
    private final UUID destinationId;
    private final String destinationName;
    private final String destinationCountry;

    public TransportResponse(String mode, int durationMinutes, UUID destinationId,
                              String destinationName, String destinationCountry) {
        this.mode = mode;
        this.durationMinutes = durationMinutes;
        this.destinationId = destinationId;
        this.destinationName = destinationName;
        this.destinationCountry = destinationCountry;
    }

    public String getMode() {
        return mode;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }
}