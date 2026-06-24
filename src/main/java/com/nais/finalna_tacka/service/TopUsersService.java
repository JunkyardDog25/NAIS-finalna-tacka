package com.nais.finalna_tacka.service;

import com.nais.finalna_tacka.dto.TopUser;
import com.nais.finalna_tacka.repository.mongo.PlaylistRepository;
import com.nais.finalna_tacka.repository.mongo.SongRepository;
import com.nais.finalna_tacka.repository.mongo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Složena sekcija izveštaja: "Top korisnici po popularnosti plejlisti" — kombinuje OBE baze.
 *
 * <p>Vlasništvo i članstvo pesama ({@code playlists.ownerId}, {@code playlists.songIds}) i
 * {@code playCount} pesama dolaze iz MongoDB-a; doseg pesme (broj različitih slušalaca) iz Neo4j
 * LISTENED grafa. Agregiramo po VLASNIKU (suma preko svih njegovih plejlisti) i rangiramo po
 * ukupnom {@code playCount}-u (korisnici čije plejliste sadrže najpopularnije pesme).</p>
 */
@Service
@RequiredArgsConstructor
public class TopUsersService {

    private static final Logger log = LoggerFactory.getLogger(TopUsersService.class);

    /** Po pesmi: koliko različitih korisnika ju je slušalo (LISTENED graf). */
    private static final String LISTENERS_PER_SONG = """
            MATCH (u:User)-[:LISTENED]->(s:Song)
            RETURN s.songId AS songId, count(DISTINCT u) AS listeners
            """;

    private final Neo4jClient neo4jClient;
    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;

    public List<TopUser> topByPlaylistPopularity(int limit) {
        // Mongo: playCount po pesmi i username po korisniku.
        Map<String, Long> playCountBySong = new HashMap<>();
        songRepository.findAll().forEach(song -> playCountBySong.put(song.getId(), song.getPlayCount()));

        Map<String, String> usernameByUserId = new HashMap<>();
        userRepository.findAll().forEach(user -> usernameByUserId.put(user.getId(), user.getUsername()));

        // Neo4j: broj različitih slušalaca po pesmi.
        Map<String, Long> listenersBySong = new HashMap<>();
        neo4jClient.query(LISTENERS_PER_SONG).fetch().all().forEach(row ->
                listenersBySong.put((String) row.get("songId"), asLong(row.get("listeners"))));

        // Agregacija po vlasniku: [totalPlayCount, totalListeners, playlistCount].
        Map<String, long[]> byOwner = new HashMap<>();
        playlistRepository.findAll().forEach(playlist -> {
            List<String> songIds = playlist.getSongIds() == null ? List.of() : playlist.getSongIds();
            long playCount = songIds.stream().mapToLong(id -> playCountBySong.getOrDefault(id, 0L)).sum();
            long listeners = songIds.stream().mapToLong(id -> listenersBySong.getOrDefault(id, 0L)).sum();

            long[] agg = byOwner.computeIfAbsent(playlist.getOwnerId(), k -> new long[3]);
            agg[0] += playCount;
            agg[1] += listeners;
            agg[2] += 1;
        });

        List<TopUser> top = byOwner.entrySet().stream()
                .map(entry -> {
                    String ownerId = entry.getKey();
                    long[] agg = entry.getValue();
                    return new TopUser(ownerId,
                            usernameByUserId.getOrDefault(ownerId, ownerId),
                            (int) agg[2], agg[0], agg[1]);
                })
                .sorted(Comparator.comparingLong(TopUser::totalPlayCount).reversed())
                .limit(limit)
                .toList();

        log.info("Top {} korisnika po popularnosti plejlisti (Mongo playlists+playCount + Neo4j LISTENED)", limit);
        return top;
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
