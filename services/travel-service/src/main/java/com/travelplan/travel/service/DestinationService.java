package com.travelplan.travel.service;

import com.travelplan.travel.dto.CreateDestinationRequest;
import com.travelplan.travel.dto.DestinationResponse;
import com.travelplan.travel.dto.UpdateDestinationRequest;
import com.travelplan.travel.entity.Destination;
import com.travelplan.travel.exception.DestinationNotFoundException;
import com.travelplan.travel.repository.DestinationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for the {@link Destination} node.
 *
 * Increment 1 scope: basic CRUD only, no relationship/graph traversal logic.
 *
 * "Delete" always means soft-delete: {@code deletedAt} is set to now(), the
 * node stays. findById / findAll silently filter out soft-deleted nodes
 * (callers receive a 404 / empty list, not a soft-deleted node).
 */
@Service
@Transactional(readOnly = true)
public class DestinationService {

    private final DestinationRepository destinationRepository;

    public DestinationService(DestinationRepository destinationRepository) {
        this.destinationRepository = destinationRepository;
    }

    /**
     * Create a new destination.
     */
    @Transactional
    public DestinationResponse create(CreateDestinationRequest request) {
        Destination destination = new Destination(request.getName(), request.getCountry());
        Destination saved = destinationRepository.save(destination);
        return DestinationResponse.from(saved);
    }

    /**
     * Find an active destination by id.
     *
     * @throws DestinationNotFoundException if the destination does not exist or is soft-deleted
     */
    public DestinationResponse findById(UUID id) {
        Destination destination = destinationRepository.findActiveById(id)
                .orElseThrow(() -> new DestinationNotFoundException(id));
        return DestinationResponse.from(destination);
    }

    /**
     * Return all active destinations.
     */
    public List<DestinationResponse> findAll() {
        return destinationRepository.findAllActive().stream()
                .map(DestinationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Replace the mutable fields ({@code name}, {@code country}) of an active destination.
     *
     * @throws DestinationNotFoundException if the destination does not exist or is soft-deleted
     */
    @Transactional
    public DestinationResponse update(UUID id, UpdateDestinationRequest request) {
        Destination destination = destinationRepository.findActiveById(id)
                .orElseThrow(() -> new DestinationNotFoundException(id));
        destination.setName(request.getName());
        destination.setCountry(request.getCountry());
        Destination saved = destinationRepository.save(destination);
        return DestinationResponse.from(saved);
    }

    /**
     * Soft-delete an active destination (sets deletedAt = now()).
     *
     * @throws DestinationNotFoundException if the destination does not exist or is already soft-deleted
     */
    @Transactional
    public void delete(UUID id) {
        Destination destination = destinationRepository.findActiveById(id)
                .orElseThrow(() -> new DestinationNotFoundException(id));
        destination.setDeletedAt(OffsetDateTime.now());
        destinationRepository.save(destination);
    }
}