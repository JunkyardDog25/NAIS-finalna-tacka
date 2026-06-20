package com.nais.finalna_tacka.domain.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for a song.
 * NOTE: {@code id} must match the id of the corresponding Neo4j
 * {@link com.nais.finalna_tacka.domain.graph.Song} node.
 */
@Data
@Document(collection = "songs")
public class Song {

    @Id
    private String id;

    private String title;

    private String artistId;

    private String albumId;

    private String genre;

    private int durationSeconds;

    private long playCount;

    private Instant createdAt;
}
