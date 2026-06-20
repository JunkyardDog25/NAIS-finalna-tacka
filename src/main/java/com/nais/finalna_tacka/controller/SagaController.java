package com.nais.finalna_tacka.controller;

import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.repository.mongo.SagaStateRepository;
import com.nais.finalna_tacka.saga.orchestrator.SagaOrchestrator;
import com.nais.finalna_tacka.saga.state.SagaState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kicks off sagas and exposes their state for the demo. The POST/DELETE endpoints return
 * immediately with a sagaId (the saga runs asynchronously over RabbitMQ); poll
 * {@code GET /api/sagas/{sagaId}} to watch the status advance.
 */
@RestController
public class SagaController {

    private final SagaOrchestrator orchestrator;
    private final SagaStateRepository sagaStateRepository;

    public SagaController(SagaOrchestrator orchestrator, SagaStateRepository sagaStateRepository) {
        this.orchestrator = orchestrator;
        this.sagaStateRepository = sagaStateRepository;
    }

    /** Start a PUBLISH_SONG saga. Returns the sagaId to track the flow. */
    @PostMapping("/api/songs")
    public ResponseEntity<Map<String, String>> publishSong(@RequestBody Song song) {
        String sagaId = orchestrator.startPublishSong(song);
        return ResponseEntity.accepted().body(Map.of("sagaId", sagaId));
    }

    /** Start a DELETE_SONG saga. Returns the sagaId to track the flow. */
    @DeleteMapping("/api/songs/{id}")
    public ResponseEntity<Map<String, String>> deleteSong(@PathVariable String id) {
        String sagaId = orchestrator.startDeleteSong(id);
        return ResponseEntity.accepted().body(Map.of("sagaId", sagaId));
    }

    /** Inspect a saga's current state (used to watch the flow during the demo). */
    @GetMapping("/api/sagas/{sagaId}")
    public ResponseEntity<SagaState> getSaga(@PathVariable String sagaId) {
        return sagaStateRepository.findById(sagaId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
