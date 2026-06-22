package com.nais.finalna_tacka.domain.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Neo4j node for an artist.
 * NOTE: {@code artistId} MUST equal the {@code id} of the corresponding MongoDB
 * {@link com.nais.finalna_tacka.domain.mongo.Artist} document.
 */
@Data
@Node("Artist")
public class Artist {

    @Id
    private String artistId;

    private String name;
}
