package com.nais.finalna_tacka.repository.mongo;

import com.nais.finalna_tacka.domain.mongo.Artist;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ArtistRepository extends MongoRepository<Artist, String> {
}
