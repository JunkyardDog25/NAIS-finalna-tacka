package com.nais.finalna_tacka.repository.graph;

import com.nais.finalna_tacka.domain.graph.Song;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SongNodeRepository extends Neo4jRepository<Song, String> {
}
