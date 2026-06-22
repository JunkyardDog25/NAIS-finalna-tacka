package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.dto.SongRecommendation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Složena sekcija izveštaja: collaborative filtering preko Neo4j LISTENED grafa.
 * Zavisi samo od {@link Neo4jClient} — title dolazi sa {@code :Song} čvora (PUBLISH_SONG).
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final String COLLABORATIVE_FILTERING = """
            MATCH (me:User {userId: $userId})-[:LISTENED]->(:Song)<-[:LISTENED]-(other:User)
            WHERE me <> other
            MATCH (other)-[:LISTENED]->(rec:Song)
            WHERE NOT (me)-[:LISTENED]->(rec)
            RETURN rec.songId AS songId,
                   rec.title  AS title,
                   count(DISTINCT other) AS poklapanje
            ORDER BY poklapanje DESC
            LIMIT 10
            """;

    private final Neo4jClient neo4jClient;

    public List<SongRecommendation> recommendFor(String userId) {
        List<SongRecommendation> results = neo4jClient.query(COLLABORATIVE_FILTERING)
                .bind(userId).to("userId")
                .fetchAs(SongRecommendation.class)
                .mappedBy((typeSystem, record) -> new SongRecommendation(
                        record.get("songId").asString(),
                        record.get("title").asString(null),
                        record.get("poklapanje").asLong()))
                .all()
                .stream()
                .toList();

        if (results.isEmpty()) {
            log.info("No CF recommendations for user {}", userId);
        }

        return results;
    }
}
