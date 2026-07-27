package com.travelplan.travel.controller;

import com.travelplan.travel.dto.CreateTransportRequest;
import com.travelplan.travel.dto.TransportResponse;
import com.travelplan.travel.service.TransportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the {@code TRANSPORT} relationship between two
 * destinations.
 *
 * No business logic here — all decisions are delegated to
 * {@link TransportService}. Exception-to-HTTP mapping is handled by
 * {@link com.travelplan.travel.exception.GlobalExceptionHandler}.
 *
 * Increment 2 scope: create a single-hop directed transport link, and list
 * destinations reachable in exactly one hop. No pathfinding, no
 * update/delete on transports.
 */
@RestController
@RequestMapping("/destinations")
public class TransportController {

    private final TransportService transportService;

    public TransportController(TransportService transportService) {
        this.transportService = transportService;
    }

    /**
     * Create a directed transport link from {@code fromId} to the
     * destination given in the request body.
     *
     * @return 201 Created with the created link, 400 on a request rule
     *         violation (self-loop, invalid mode, non-positive duration),
     *         404 if origin or target is absent/soft-deleted
     */
    @PostMapping("/{fromId}/transports")
    public ResponseEntity<TransportResponse> create(@PathVariable UUID fromId,
                                                      @Valid @RequestBody CreateTransportRequest request) {
        TransportResponse created = transportService.create(fromId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * List destinations reachable from {@code id} via one outgoing transport
     * hop.
     *
     * @return 200 with the list (empty list if none), 404 if {@code id} is
     *         absent/soft-deleted
     */
    @GetMapping("/{id}/transports")
    public ResponseEntity<List<TransportResponse>> getOutgoing(@PathVariable UUID id) {
        return ResponseEntity.ok(transportService.findOutgoing(id));
    }
}