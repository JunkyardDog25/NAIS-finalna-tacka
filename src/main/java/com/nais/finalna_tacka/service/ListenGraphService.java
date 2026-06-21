package com.nais.finalna_tacka.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Neo4j strana RECORD_LISTEN sage. Poziva je {@code GraphSagaParticipant} kada stigne
 * {@code GraphRecordListen} komanda.
 *
 * <p>Graf je terminalni korak u ovoj sagi, pa je {@link #compensateRecordListen}
 * dokumentovana radi odbrane, ali nije ukačena u orkestrator.</p>
 */
@Service
public class ListenGraphService {

    private static final Logger log = LoggerFactory.getLogger(ListenGraphService.class);

    private static final String RECORD_LISTEN = """
            MERGE (u:User {userId: $userId})
            MERGE (s:Song {songId: $songId})
            MERGE (u)-[r:LISTENED]->(s)
            ON CREATE SET r.count = 1
            ON MATCH SET r.count = r.count + 1
            """;

    private static final String READ_LISTEN_COUNT = """
            MATCH (u:User {userId: $userId})-[r:LISTENED]->(s:Song {songId: $songId})
            RETURN r.count AS count
            """;

    private static final String DECREMENT_LISTEN_COUNT = """
            MATCH (u:User {userId: $userId})-[r:LISTENED]->(s:Song {songId: $songId})
            SET r.count = r.count - 1
            """;

    private static final String DELETE_LISTEN_REL = """
            MATCH (u:User {userId: $userId})-[r:LISTENED]->(s:Song {songId: $songId})
            DELETE r
            """;

    private final Neo4jClient neo4jClient;
    private final boolean failListen;

    public ListenGraphService(Neo4jClient neo4jClient,
                              @Value("${saga.graph.fail-listen:false}") boolean failListen) {
        this.neo4jClient = neo4jClient;
        this.failListen = failListen;
    }

/**
     * Upsert LISTENED relacije i inkrement count-a. Idempotentno pri ponovnoj isporuci
     * samo dok saga nije dostigla terminalno stanje (kao i ostali koraci sage).
     */
    public void recordListen(String userId, String songId) {
        if (failListen) {
            throw new IllegalStateException(
                    "Forced graph failure (saga.graph.fail-listen=true) to demo compensation");
        }

        neo4jClient.query(RECORD_LISTEN)
                .bind(userId).to("userId")
                .bind(songId).to("songId")
                .run();

        log.info("Recorded listen in graph: user {} -> song {}", userId, songId);
    }

/**
     * Poništava jedan inkrement slušanja. NE poziva je orkestrator — graf je terminalni korak
     * u RECORD_LISTEN (Mongo → Neo4j), pa nijedan kasniji korak ne može da padne posle uspešnog
     * upisa u graf.
     *
     * <p>Ako je count bio 1: briše se relacija (naivni count-- bi ostavio mrtvu relaciju sa
     * count=0). Ako je count &gt; 1: dekrement.</p>
     */
    public void compensateRecordListen(String userId, String songId) {
        Long count = neo4jClient.query(READ_LISTEN_COUNT)
                .bind(userId).to("userId")
                .bind(songId).to("songId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("count").asLong())
                .one()
                .orElse(null);

        if (count == null) {
            log.info("No LISTENED rel to compensate for user {} -> song {} (no-op)", userId, songId);
            return;
        }

        if (count <= 1) {
            neo4jClient.query(DELETE_LISTEN_REL)
                    .bind(userId).to("userId")
                    .bind(songId).to("songId")
                    .run();
            log.info("Compensated listen in graph: deleted LISTENED rel user {} -> song {}", userId, songId);
        } else {
            neo4jClient.query(DECREMENT_LISTEN_COUNT)
                    .bind(userId).to("userId")
                    .bind(songId).to("songId")
                    .run();
            log.info("Compensated listen in graph: decremented LISTENED count user {} -> song {} (was {})",
                    userId, songId, count);
        }
    }
}
