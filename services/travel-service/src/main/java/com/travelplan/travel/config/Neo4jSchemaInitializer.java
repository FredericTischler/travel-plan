package com.travelplan.travel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;

/**
 * Ensures the {@code Destination.id} uniqueness constraint exists at startup.
 *
 * No Flyway / neo4j-migrations tool is used here: for a single node type with
 * a single, simple constraint, an idempotent Cypher statement run once at
 * boot is simpler than introducing a dedicated migration tool and its own
 * bookkeeping (schema history node, checksum tracking, etc.). Reconsider this
 * choice if the graph model grows (multiple node types, relationships,
 * ordered/versioned schema changes).
 *
 * {@code CREATE CONSTRAINT ... IF NOT EXISTS} is idempotent: safe to run on
 * every startup, including against an already-initialised database.
 */
@Configuration
public class Neo4jSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(Neo4jSchemaInitializer.class);

    @Bean
    CommandLineRunner ensureDestinationIdUniqueConstraint(Neo4jClient neo4jClient) {
        return args -> {
            neo4jClient.query(
                            "CREATE CONSTRAINT destination_id_unique IF NOT EXISTS "
                                    + "FOR (d:Destination) REQUIRE d.id IS UNIQUE")
                    .run();
            log.info("Neo4j schema constraint ensured: Destination.id IS UNIQUE");
        };
    }
}