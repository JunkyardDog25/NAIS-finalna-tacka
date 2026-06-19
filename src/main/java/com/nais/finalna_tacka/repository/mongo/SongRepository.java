package com.nais.finalna_tacka.repository.mongo;

import com.nais.finalna_tacka.domain.mongo.Song;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SongRepository extends MongoRepository<Song, String> {
}
