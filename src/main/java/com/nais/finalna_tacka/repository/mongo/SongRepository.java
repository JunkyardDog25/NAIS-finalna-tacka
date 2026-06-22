package com.nais.finalna_tacka.repository.mongo;

import com.nais.finalna_tacka.domain.mongo.Song;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SongRepository extends MongoRepository<Song, String> {

    List<Song> findByDurationSecondsBetweenOrderByPlayCountDesc(int min, int max);

    List<Song> findByGenreOrderByPlayCountDesc(String genre);
}
