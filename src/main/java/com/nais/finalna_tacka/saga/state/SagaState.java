package com.nais.finalna_tacka.saga.state;

import com.nais.finalna_tacka.domain.mongo.Song;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persisted state of a single saga instance. Stored in MongoDB so the orchestrator is
 * stateful (survives restarts) and idempotent (each reply is resolved against the stored
 * status).
 *
 * <p>The {@code payload} snapshot keeps the full {@link Song} so that compensation on a
 * DELETE_SONG flow can re-create the document/node from the saved data.</p>
 */
@Data
@Document(collection = "saga_state")
public class SagaState {

    @Id
    private String sagaId;

    private SagaType sagaType;
    private SagaStatus status;

    /** Snapshot of the song; needed to compensate (re-create) on a delete flow. */
    private Song payload;

    private Instant createdAt;
    private Instant updatedAt;
}
