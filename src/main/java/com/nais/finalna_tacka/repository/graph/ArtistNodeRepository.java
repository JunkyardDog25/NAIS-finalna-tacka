package com.nais.finalna_tacka.repository.graph;

import com.nais.finalna_tacka.domain.graph.Artist;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface ArtistNodeRepository extends Neo4jRepository<Artist, String> {
}
