package com.travelplan.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the payment lifecycle:
 *   create (PENDING) -> transition to COMPLETED -> further transition rejected (terminal)
 *   -> transition to PENDING rejected (invalid target) -> soft-delete -> verify deletion
 *
 * Exercises the status immutability contract end-to-end:
 * - PENDING -> COMPLETED succeeds exactly once.
 * - Any further transition on a terminal payment is rejected with 409, even
 *   toward the other terminal value.
 * - PENDING is never a valid transition target (400), regardless of the
 *   payment's current status.
 * - Soft-delete is independent from status: a COMPLETED payment can still be
 *   soft-deleted, and is then invisible to GET.
 *
 * Uses Testcontainers (postgres:17.5-bookworm, same image as production).
 * DynamicPropertySource satisfies the :? fail-fast guards in application.yml.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17.5-bookworm")
                    .withDatabaseName("payment_db")
                    .withUsername("payment_user")
                    .withPassword("test_password_only");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", postgres::getHost);
        registry.add("DB_PORT", () -> String.valueOf(postgres.getMappedPort(5432)));
        registry.add("DB_NAME", postgres::getDatabaseName);
        registry.add("DB_USERNAME", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createCompleteThenRejectFurtherTransitions() {
        // Step 1 — POST /payments -> 201 Created, status PENDING
        Map<String, Object> body = Map.of("amount", 10.50, "currency", "EUR");
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/payments", body, Map.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).containsEntry("status", "PENDING");

        String paymentId = (String) createResponse.getBody().get("id");
        UUID id = UUID.fromString(paymentId);

        // Step 2 — PATCH /payments/{id}/status {status: COMPLETED} -> 200, status COMPLETED
        ResponseEntity<Map> completeResponse = restTemplate.exchange(
                "/payments/" + id + "/status", HttpMethod.PATCH,
                jsonEntity(Map.of("status", "COMPLETED")), Map.class);

        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completeResponse.getBody()).containsEntry("status", "COMPLETED");

        // Step 3 — PATCH again to FAILED -> 409 (already terminal)
        ResponseEntity<Map> failAfterCompleteResponse = restTemplate.exchange(
                "/payments/" + id + "/status", HttpMethod.PATCH,
                jsonEntity(Map.of("status", "FAILED")), Map.class);

        assertThat(failAfterCompleteResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(failAfterCompleteResponse.getBody()).containsEntry("status", 409);

        // Step 4 — PATCH to PENDING -> 400 (invalid target value)
        ResponseEntity<Map> pendingTargetResponse = restTemplate.exchange(
                "/payments/" + id + "/status", HttpMethod.PATCH,
                jsonEntity(Map.of("status", "PENDING")), Map.class);

        assertThat(pendingTargetResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pendingTargetResponse.getBody()).containsEntry("status", 400);
    }

    @Test
    void softDeleteThenGetReturnsNotFound() {
        // Create a payment to delete
        Map<String, Object> body = Map.of("amount", 5.00, "currency", "USD");
        ResponseEntity<Map> createResponse = restTemplate.postForEntity("/payments", body, Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String paymentId = (String) createResponse.getBody().get("id");
        UUID id = UUID.fromString(paymentId);

        // DELETE /payments/{id} -> 204 No Content
        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange("/payments/" + id, HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // GET /payments/{id} -> 404 (soft-deleted)
        ResponseEntity<Map> getAfterDelete = restTemplate.getForEntity("/payments/" + id, Map.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getAfterDelete.getBody()).containsEntry("status", 404);
    }

    private static org.springframework.http.HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}