package com.nais.finalna_tacka.repository.mongo;

import com.nais.finalna_tacka.domain.mongo.Album;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlbumRepository extends MongoRepository<Album, String> {
}
