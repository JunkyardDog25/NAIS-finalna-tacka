package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.domain.mongo.Playlist;
import com.nais.finalna_tacka.dto.GenrePlaylistRecommendation;
import com.nais.finalna_tacka.repository.mongo.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Složena sekcija izveštaja: "Preporuke po žanru iz naziva plejliste" — kombinuje OBE baze.
 *
 * <p>Iz MongoDB-a uzimamo plejliste korisnika (njihove nazive + skup pesama koje su već u njima);
 * iz Neo4j IN_GENRE grafa kandidate pesama po žanru. Ako naziv plejliste sadrži ime žanra
 * (npr. "Rock Classics" sadrži "rock"), preporučujemo pesme tog žanra koje korisnik još NEMA ni
 * u jednoj svojoj plejlisti.</p>
 */
@Service
@RequiredArgsConstructor
public class GenreRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(GenreRecommendationService.class);

    /** Kandidati: sve pesme grupisane po žanru iz Neo4j grafa (:Song)-[:IN_GENRE]->(:Genre). */
    private static final String SONGS_BY_GENRE = """
            MATCH (s:Song)-[:IN_GENRE]->(g:Genre)
            RETURN g.name AS genre, s.songId AS songId, s.title AS title
            """;

    private final Neo4jClient neo4jClient;
    private final PlaylistRepository playlistRepository;

    public List<GenrePlaylistRecommendation> recommendFor(String userId) {
        // Mongo: plejliste korisnika -> nazivi + skup pesama koje su već u njima (exclude).
        List<Playlist> playlists = playlistRepository.findByOwnerId(userId);
        if (playlists.isEmpty()) {
            log.info("No playlists for user {} -> no genre recommendations", userId);
            return List.of();
        }
        Set<String> alreadyInPlaylists = playlists.stream()
                .flatMap(p -> (p.getSongIds() == null ? List.<String>of() : p.getSongIds()).stream())
                .collect(Collectors.toSet());

        // Neo4j: kandidati pesama po žanru.
        Map<String, List<SongRef>> songsByGenre = new HashMap<>();
        neo4jClient.query(SONGS_BY_GENRE).fetch().all().forEach(row ->
                songsByGenre.computeIfAbsent((String) row.get("genre"), k -> new ArrayList<>())
                        .add(new SongRef((String) row.get("songId"), (String) row.get("title"))));

        // Za svaku plejlistu čiji naziv sadrži ime žanra -> preporuči pesme tog žanra koje nisu već u plejlistama.
        List<GenrePlaylistRecommendation> recommendations = new ArrayList<>();
        Set<String> seen = new HashSet<>(); // dedupe po (playlistId|songId)
        for (Playlist playlist : playlists) {
            String nameLower = playlist.getName() == null ? "" : playlist.getName().toLowerCase();
            for (Map.Entry<String, List<SongRef>> entry : songsByGenre.entrySet()) {
                String genre = entry.getKey();
                if (genre == null || genre.isBlank() || !nameLower.contains(genre.toLowerCase())) {
                    continue;
                }
                for (SongRef song : entry.getValue()) {
                    if (alreadyInPlaylists.contains(song.songId())) {
                        continue;
                    }
                    if (seen.add(playlist.getId() + "|" + song.songId())) {
                        recommendations.add(new GenrePlaylistRecommendation(song.title(), playlist.getName()));
                    }
                }
            }
        }

        log.info("Genre-from-playlist recommendations for user {}: {}", userId, recommendations.size());
        return recommendations;
    }

    private record SongRef(String songId, String title) {}
}
