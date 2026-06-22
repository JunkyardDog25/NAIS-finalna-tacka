package com.nais.finalna_tacka.saga.state;

import com.nais.finalna_tacka.domain.mongo.Song;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Perzistirano stanje jedne saga instance. Čuva se u MongoDB-u tako da je orkestrator
 * stateful (preživljava restartove) i idempotentan (svaki odgovor se razrešava u odnosu na
 * sačuvani status).
 *
 * <p>{@code payload} snapshot drži kompletan {@link Song} tako da kompenzacija u DELETE_SONG
 * toku može ponovo da kreira dokument/čvor iz sačuvanih podataka.</p>
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

    /** Payload za RECORD_LISTEN sage (userId + songId). */
    private ListenPayload listenPayload;

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

    /** Create a fresh RECORD_LISTEN saga in {@link SagaStatus#STARTED}. */
    public static SagaState startListen(ListenPayload listenPayload) {
        Instant now = Instant.now();
        SagaState state = new SagaState();
        state.sagaId = UUID.randomUUID().toString();
        state.sagaType = SagaType.RECORD_LISTEN;
        state.status = SagaStatus.STARTED;
        state.listenPayload = listenPayload;
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
