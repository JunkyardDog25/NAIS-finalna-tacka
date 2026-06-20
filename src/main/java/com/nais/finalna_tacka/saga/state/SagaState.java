package com.nais.finalna_tacka.saga.state;

import com.nais.finalna_tacka.domain.mongo.Song;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

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

    /** Create a fresh saga in {@link SagaStatus#STARTED} with a generated id and timestamps. */
    public static SagaState start(SagaType sagaType, Song payload) {
        Instant now = Instant.now();
        SagaState state = new SagaState();
        state.sagaId = UUID.randomUUID().toString();
        state.sagaType = sagaType;
        state.status = SagaStatus.STARTED;
        state.payload = payload;
        state.createdAt = now;
        state.updatedAt = now;
        return state;
    }

    /** Move to a new status and bump {@code updatedAt}. */
    public void updateStatus(SagaStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
}
