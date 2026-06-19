package com.nais.finalna_tacka.repository.graph;

import com.nais.finalna_tacka.domain.graph.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface UserNodeRepository extends Neo4jRepository<User, String> {
}
