package com.nais.finalna_tacka.domain.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document for a user.
 * NOTE: {@code id} must match the id of the corresponding Neo4j
 * {@link com.nais.finalna_tacka.domain.graph.User} node so the two stores stay in sync.
 */
@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String username;

    private String email;

    private String displayName;
}
