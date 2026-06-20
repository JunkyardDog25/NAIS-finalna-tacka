package com.nais.finalna_tacka.controller;

import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple connectivity check for both data stores so we can confirm the app can reach
 * MongoDB and Neo4j. Returns HTTP 200 only when both pings succeed.
 *
 * <p>TODO (teammates): add report/business controllers in this package. They should
 * delegate to the {@code service} layer and never talk to repositories directly.</p>
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final Driver neo4jDriver;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();

        boolean mongoUp = pingMongo(body);
        boolean neo4jUp = pingNeo4j(body);

        boolean allUp = mongoUp && neo4jUp;
        body.put("status", allUp ? "UP" : "DOWN");

        return ResponseEntity
                .status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private boolean pingMongo(Map<String, Object> body) {
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            body.put("mongo", "UP");
            return true;
        } catch (Exception e) {
            body.put("mongo", "DOWN: " + e.getMessage());
            return false;
        }
    }

    private boolean pingNeo4j(Map<String, Object> body) {
        try {
            neo4jDriver.verifyConnectivity();
            body.put("neo4j", "UP");
            return true;
        } catch (Exception e) {
            body.put("neo4j", "DOWN: " + e.getMessage());
            return false;
        }
    }
}
