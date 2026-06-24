package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.saga.state.PlaylistPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Neo4j side of the CREATE_PLAYLIST saga. Invoked by {@code GraphSagaParticipant} when a
 * {@code GraphCreatePlaylist} command arrives.
 *
 * <p>Stores ONLY ownership: an explicit Cypher MERGE (not repository.save) creates the
 * Playlist node + OWNED_BY relationship idempotently — a redelivered command merges into the
 * same graph instead of duplicating it. Song containment is NOT mirrored here (no CONTAINS
 * edges); the authoritative list of songs lives in the Mongo document.</p>
 */
@Service
public class PlaylistGraphService {

    private static final Logger log = LoggerFactory.getLogger(PlaylistGraphService.class);

    // Single idempotent Cypher statement: ownership only, no song edges.
    private static final String MERGE_PLAYLIST = """
            MERGE (pl:Playlist {playlistId: $playlistId}) SET pl.name = $name
            MERGE (u:User {userId: $ownerId})
            MERGE (pl)-[:OWNED_BY]->(u)
            """;

    private final Neo4jClient neo4jClient;

    /** Demo toggle: when true the create step throws, exercising the saga compensation path. */
    private final boolean failCreate;

    public PlaylistGraphService(Neo4jClient neo4jClient,
                                @Value("${saga.graph.fail-create-playlist:false}") boolean failCreate) {
        this.neo4jClient = neo4jClient;
        this.failCreate = failCreate;
    }

    public void createPlaylist(PlaylistPayload p) {
        if (failCreate) {
            throw new IllegalStateException(
                    "Forced graph failure (saga.graph.fail-create-playlist=true) to demo compensation");
        }

        // Bind ownership fields only; songIds are intentionally NOT used in the graph.
        neo4jClient.query(MERGE_PLAYLIST)
                .bind(p.playlistId()).to("playlistId")
                .bind(p.name()).to("name")
                .bind(p.ownerId()).to("ownerId")
                .run();

        log.info("Merged playlist {} graph: (:Playlist)-[:OWNED_BY]->(:User {})",
                p.playlistId(), p.ownerId());
    }
}
