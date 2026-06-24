package com.nais.finalna_tacka.repository.graph;

import com.nais.finalna_tacka.domain.graph.Playlist;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface PlaylistNodeRepository extends Neo4jRepository<Playlist, String> {
}
