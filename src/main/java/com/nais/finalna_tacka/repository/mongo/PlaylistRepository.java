package com.nais.finalna_tacka.repository.mongo;

import com.nais.finalna_tacka.domain.mongo.Playlist;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlaylistRepository extends MongoRepository<Playlist, String> {
}
