package com.nais.finalna_tacka.repository.mongo;

import com.nais.finalna_tacka.domain.mongo.Playlist;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PlaylistRepository extends MongoRepository<Playlist, String> {

    List<Playlist> findByOwnerId(String ownerId);
}
