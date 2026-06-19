package com.nais.finalna_tacka.domain.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * Neo4j node for a user.
 * NOTE: {@code userId} MUST equal the {@code id} of the corresponding MongoDB
 * {@link com.nais.finalna_tacka.domain.mongo.User} document.
 */
@Data
@Node("User")
public class User {

    @Id
    private String userId;
    private String username;

    @Relationship(type = "FOLLOWS", direction = Relationship.Direction.OUTGOING)
    private Set<User> follows = new HashSet<>();

    @Relationship(type = "LISTENED", direction = Relationship.Direction.OUTGOING)
    private Set<ListenedRel> listened = new HashSet<>();
}
