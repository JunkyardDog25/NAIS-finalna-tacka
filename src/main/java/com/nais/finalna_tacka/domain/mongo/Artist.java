package com.nais.finalna_tacka.domain.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document for an artist.
 * NOTE: {@code id} must match the id of the corresponding Neo4j
 * {@link com.nais.finalna_tacka.domain.graph.Artist} node.
 */
@Data
@Document(collection = "artists")
public class Artist {

    @Id
    private String id;

    private String name;

    private String country;
}
