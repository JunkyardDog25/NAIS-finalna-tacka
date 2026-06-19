package com.nais.finalna_tacka.repository.graph;

import com.nais.finalna_tacka.domain.graph.Genre;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface GenreNodeRepository extends Neo4jRepository<Genre, String> {
}
