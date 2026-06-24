package com.nais.finalna_tacka.domain.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * Neo4j node for a playlist.
 * NOTE: {@code playlistId} MUST equal the {@code id} of the corresponding MongoDB
 * {@link com.nais.finalna_tacka.domain.mongo.Playlist} document.
 *
 * <p>The graph stores ONLY ownership ((:Playlist)-[:OWNED_BY]->(:User)); song containment
 * lives exclusively in the authoritative Mongo document (no CONTAINS edges here).</p>
 */
@Data
@Node("Playlist")
public class Playlist {

    @Id
    private String playlistId;

    private String name;

    @Relationship(type = "OWNED_BY", direction = Relationship.Direction.OUTGOING)
    private User owner;
}
