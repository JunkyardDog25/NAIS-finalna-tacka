package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.domain.mongo.Artist;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.repository.mongo.ArtistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

/**
 * Neo4j side of the PUBLISH_SONG saga. Invoked by {@code GraphSagaParticipant} when a
 * {@code GraphCreateSong} command arrives.
 *
 * <p>Uses an explicit Cypher MERGE (not repository.save) so the node + BY/IN_GENRE
 * relationships are created idempotently — a redelivered command merges into the same
 * graph instead of duplicating it.</p>
 */
@Service
public class SongGraphService {

    private static final Logger log = LoggerFactory.getLogger(SongGraphService.class);

    // Single idempotent Cypher statement: one MERGE per node/relationship.
    private static final String MERGE_SONG = """
            MERGE (s:Song {songId: $songId}) SET s.title = $title
            MERGE (a:Artist {artistId: $artistId}) SET a.name = $artistName
            MERGE (g:Genre {name: $genre})
            MERGE (s)-[:BY]->(a)
            MERGE (s)-[:IN_GENRE]->(g)
            """;

    private final Neo4jClient neo4jClient;
    private final ArtistRepository artistRepository;

    /** Demo toggle: when true the create step throws, exercising the saga compensation path. */
    private final boolean failCreate;

    public SongGraphService(Neo4jClient neo4jClient,
                            ArtistRepository artistRepository,
                            @Value("${saga.graph.fail-create:false}") boolean failCreate) {
        this.neo4jClient = neo4jClient;
        this.artistRepository = artistRepository;
        this.failCreate = failCreate;
    }

    public void createSong(Song song) {
        if (failCreate) {
            throw new IllegalStateException(
                    "Forced graph failure (saga.graph.fail-create=true) to demo compensation");
        }

        // The Song document only carries artistId; resolve the display name from Mongo.
        String artistName = artistRepository.findById(song.getArtistId())
                .map(Artist::getName)
                .orElse(song.getArtistId());

        neo4jClient.query(MERGE_SONG)
                .bind(song.getId()).to("songId")
                .bind(song.getTitle()).to("title")
                .bind(song.getArtistId()).to("artistId")
                .bind(artistName).to("artistName")
                .bind(song.getGenre()).to("genre")
                .run();

        log.info("Merged song {} graph: (:Song)-[:BY]->(:Artist {}), (:Song)-[:IN_GENRE]->(:Genre {})",
                song.getId(), song.getArtistId(), song.getGenre());
    }
}
