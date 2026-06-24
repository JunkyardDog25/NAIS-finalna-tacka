package com.nais.finalna_tacka.controller;

import com.nais.finalna_tacka.dto.CreatePlaylistRequest;
import com.nais.finalna_tacka.saga.orchestrator.SagaOrchestrator;
import com.nais.finalna_tacka.saga.state.PlaylistPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pokreće CREATE_PLAYLIST sage. Vraća odmah sa sagaId; pozivaj
 * {@code GET /api/sagas/{sagaId}} da pratiš napredovanje statusa.
 */
@RestController
@RequiredArgsConstructor
public class PlaylistController {

    private final SagaOrchestrator orchestrator;

    /** Start a CREATE_PLAYLIST saga. Returns the sagaId to track the flow. */
    @PostMapping("/api/playlists")
    public ResponseEntity<Map<String, String>> createPlaylist(@Valid @RequestBody CreatePlaylistRequest request) {
        // Shared id: generate once here so Mongo and Neo4j use the same playlist id.
        String playlistId = UUID.randomUUID().toString();
        List<String> songIds = request.songIds() == null ? List.of() : request.songIds();
        PlaylistPayload payload = new PlaylistPayload(playlistId, request.ownerId(), request.name(), songIds);
        String sagaId = orchestrator.startCreatePlaylist(payload);
        return ResponseEntity.accepted().body(Map.of("sagaId", sagaId));
    }
}
