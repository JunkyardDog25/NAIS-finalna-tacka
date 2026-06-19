package com.nais.finalna_tacka.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Splits Spring Data repository scanning between the two stores so MongoDB and Neo4j
 * repositories are never ambiguously assigned to the wrong store.
 *
 * <p>MongoDB repositories live under {@code repository.mongo} and Neo4j repositories
 * under {@code repository.graph}.</p>
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.nais.finalna_tacka.repository.mongo")
@EnableNeo4jRepositories(basePackages = "com.nais.finalna_tacka.repository.graph")
public class DatabaseConfig {
}
