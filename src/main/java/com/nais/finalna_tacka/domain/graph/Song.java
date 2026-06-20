package com.nais.finalna_tacka.domain.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * Neo4j node for a song.
 * NOTE: {@code songId} MUST equal the {@code id} of the corresponding MongoDB
 * {@link com.nais.finalna_tacka.domain.mongo.Song} document.
 */
@Data
@Node("Song")
public class Song {

    @Id
    private String songId;

    private String title;

    @Relationship(type = "BY", direction = Relationship.Direction.OUTGOING)
    private Artist artist;

    @Relationship(type = "IN_GENRE", direction = Relationship.Direction.OUTGOING)
    private Genre genre;
}
