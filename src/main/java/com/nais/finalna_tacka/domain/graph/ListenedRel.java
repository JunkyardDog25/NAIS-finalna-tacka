package com.nais.finalna_tacka.domain.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Relationship properties for the {@code (:User)-[:LISTENED]->(:Song)} relationship,
 * carrying how many times the user listened to the song.
 */
@Data
@RelationshipProperties
public class ListenedRel {

    @RelationshipId
    @GeneratedValue
    private Long id;

    @TargetNode
    private Song song;

    private long count;
}
