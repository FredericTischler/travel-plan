package com.travelplan.travel;

import com.travelplan.travel.support.TestJwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the destination CRUD lifecycle:
 *   create -> read -> update -> soft-delete -> verify deletion.
 *
 * Increment 1 scope: basic CRUD on a single node type only, no relationship
 * or graph traversal exercised here.
 *
 * Uses Testcontainers (neo4j:5.26.6-community, same image as production).
 * DynamicPropertySource satisfies application.yml's required placeholders.
 * Every call below carries a Bearer token (see {@link TestJwtTokens}), since
 * every endpoint on DestinationController requires one.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class DestinationLifecycleIntegrationTest {

    @Container
    static final Neo4jContainer<?> neo4j =
            new Neo4jContainer<>("neo4j:5.26.6-community")
                    .withAdminPassword("test_password_only");

    @DynamicPropertySource
    static void registerNeo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("NEO4J_HOST", neo4j::getHost);
        registry.add("NEO4J_PORT", () -> String.valueOf(neo4j.getMappedPort(7687)));
        registry.add("NEO4J_USERNAME", () -> "neo4j");
        registry.add("NEO4J_PASSWORD", neo4j::getAdminPassword);
        registry.add("JWT_SIGNING_KEY", () -> TestJwtTokens.SIGNING_KEY);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createReadUpdateThenSoftDelete() {
        // Step 1 — POST /destinations -> 201 Created
        Map<String, Object> createBody = Map.of("name", "Lisbon", "country", "Portugal");
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/destinations", HttpMethod.POST, authorizedJsonEntity(createBody), Map.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).containsEntry("name", "Lisbon");
        assertThat(createResponse.getBody()).containsEntry("country", "Portugal");

        String destinationId = (String) createResponse.getBody().get("id");
        UUID id = UUID.fromString(destinationId);

        // Step 2 — GET /destinations/{id} -> 200, same data read back from Neo4j
        ResponseEntity<Map> getResponse = restTemplate.exchange(
                "/destinations/" + id, HttpMethod.GET, authorizedEntity(), Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).containsEntry("name", "Lisbon");
        assertThat(getResponse.getBody()).containsEntry("country", "Portugal");

        // Step 3 — PUT /destinations/{id} -> 200, fields replaced
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/destinations/" + id, HttpMethod.PUT,
                authorizedJsonEntity(Map.of("name", "Porto", "country", "Portugal")), Map.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).containsEntry("name", "Porto");

        // Step 4 — DELETE /destinations/{id} -> 204 No Content
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/destinations/" + id, HttpMethod.DELETE, authorizedEntity(), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Step 5 — GET /destinations/{id} -> 404 (soft-deleted)
        ResponseEntity<Map> getAfterDelete = restTemplate.exchange(
                "/destinations/" + id, HttpMethod.GET, authorizedEntity(), Map.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getAfterDelete.getBody()).containsEntry("status", 404);
    }

    @Test
    void getUnknownIdReturnsNotFound() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/destinations/" + UUID.randomUUID(), HttpMethod.GET, authorizedEntity(), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static HttpEntity<Map<String, Object>> authorizedJsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(TestJwtTokens.validToken());
        return new HttpEntity<>(body, headers);
    }

    private static HttpEntity<Void> authorizedEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestJwtTokens.validToken());
        return new HttpEntity<>(headers);
    }
}