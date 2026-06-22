package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.domain.mongo.Artist;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.dto.SongRow;
import com.nais.finalna_tacka.repository.mongo.SongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Prosta sekcija izveštaja: čita isključivo iz MongoDB {@code songs} kolekcije
 * i vraća ravne {@link SongRow} redove za Grafana Infinity.
 */
@Service
@RequiredArgsConstructor
public class SongReportService {

    private final SongRepository songRepository;

    public List<SongRow> byDuration(int min, int max) {
        return songRepository.findByDurationSecondsBetweenOrderByPlayCountDesc(min, max).stream()
                .map(SongReportService::toRow)
                .toList();
    }

    public List<SongRow> byGenre(String genre) {
        List<Song> songs;
        if (genre == null || genre.isBlank()) {
            songs = songRepository.findAll().stream()
                    .sorted(Comparator.comparingLong(Song::getPlayCount).reversed())
                    .toList();
        } else {
            songs = songRepository.findByGenreOrderByPlayCountDesc(genre);
        }
        return songs.stream()
                .map(SongReportService::toRow)
                .toList();
    }

    private static SongRow toRow(Song song) {
        Artist artist = song.getArtist();
        return new SongRow(
                song.getId(),
                song.getTitle(),
                artist != null ? artist.getName() : null,
                song.getGenre(),
                song.getDurationSeconds(),
                song.getPlayCount());
    }
}
