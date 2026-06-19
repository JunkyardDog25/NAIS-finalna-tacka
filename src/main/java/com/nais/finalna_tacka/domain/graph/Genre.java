package com.nais.finalna_tacka.domain.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Neo4j node for a genre. The genre name is used as the natural id.
 */
@Data
@Node("Genre")
public class Genre {

    @Id
    private String name;
}
