package com.nais.finalna_tacka.saga.orchestrator;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.repository.mongo.SagaStateRepository;
import com.nais.finalna_tacka.saga.messages.GraphCreatePlaylist;
import com.nais.finalna_tacka.saga.messages.GraphCreateSong;
import com.nais.finalna_tacka.saga.messages.GraphDeleteSong;
import com.nais.finalna_tacka.saga.messages.GraphRecordListen;
import com.nais.finalna_tacka.saga.messages.MongoCompensateListen;
import com.nais.finalna_tacka.saga.messages.MongoCreatePlaylist;
import com.nais.finalna_tacka.saga.messages.MongoCreateSong;
import com.nais.finalna_tacka.saga.messages.MongoDeletePlaylist;
import com.nais.finalna_tacka.saga.messages.MongoDeleteSong;
import com.nais.finalna_tacka.saga.messages.MongoRecordListen;
import com.nais.finalna_tacka.saga.messages.SagaReply;
import com.nais.finalna_tacka.saga.state.ListenPayload;
import com.nais.finalna_tacka.saga.state.PlaylistPayload;
import com.nais.finalna_tacka.saga.state.SagaState;
import com.nais.finalna_tacka.saga.state.SagaStatus;
import com.nais.finalna_tacka.saga.state.SagaType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Saga ORKESTRATOR. Vlasnik je toka: pokreće sagu, šalje prvu komandu i reaguje na svaki
 * {@link SagaReply} slanjem sledeće komande ili kompenzacije, perzistirajući status posle
 * svake tranzicije.
 *
 * <p>Sekvenca (happy path PUBLISH_SONG):</p>
 * <pre>
 *   start -> [STARTED] --MongoCreateSong--> mongo --reply ok--> [MONGO_DONE]
 *         --GraphCreateSong--> graph --reply ok--> [COMPLETED]
 * </pre>
 * <p>Ako graf korak padne, orkestrator kompenzuje Mongo korak i završava sa FAILED.
 * Odgovori za sagu koja je već u terminalnom stanju (COMPLETED/FAILED) se ignorišu, tako da
 * handler ostaje idempotentan pri RabbitMQ at-least-once isporuci.</p>
 */
@Component
@RequiredArgsConstructor
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final RabbitTemplate rabbitTemplate;
    private final SagaStateRepository repository;

    // --- Entry points (called by the REST controller) ---

    /** Begin a PUBLISH_SONG saga: write to Mongo first, then mirror into Neo4j. */
    public String startPublishSong(Song song) {
        // Shared id: assign once here (before publishing) so Mongo and Neo4j use the same id.
        if (song.getId() == null) {
            song.setId(UUID.randomUUID().toString());
        }
        SagaState state = newSaga(SagaType.PUBLISH_SONG, song);
        log.info("Saga {} PUBLISH_SONG started for songId={}", state.getSagaId(), song.getId());
        send(RabbitConfig.MONGO_COMMANDS_QUEUE, new MongoCreateSong(state.getSagaId(), song));
        return state.getSagaId();
    }

    /** Begin a DELETE_SONG saga: delete from Mongo first, then from Neo4j. */
    public String startDeleteSong(String songId) {
        // Snapshot the existing song so the delete can be compensated (re-created) if Neo4j fails.
        Song snapshot = new Song();
        snapshot.setId(songId);
        SagaState state = newSaga(SagaType.DELETE_SONG, snapshot);
        log.info("Saga {} DELETE_SONG started for songId={}", state.getSagaId(), songId);
        send(RabbitConfig.MONGO_COMMANDS_QUEUE, new MongoDeleteSong(state.getSagaId(), songId));
        return state.getSagaId();
    }

    /** Begin a RECORD_LISTEN saga: increment playCount in Mongo, then upsert LISTENED in Neo4j. */
    public String startRecordListen(String userId, String songId) {
        SagaState state = newSagaListen(new ListenPayload(userId, songId));
        log.info("Saga {} RECORD_LISTEN started for userId={} songId={}",
                state.getSagaId(), userId, songId);
        send(RabbitConfig.MONGO_COMMANDS_QUEUE,
                new MongoRecordListen(state.getSagaId(), userId, songId));
        return state.getSagaId();
    }

    /** Begin a CREATE_PLAYLIST saga: insert the playlist in Mongo first, then mirror ownership into Neo4j. */
    public String startCreatePlaylist(PlaylistPayload payload) {
        SagaState state = repository.save(SagaState.startCreatePlaylist(payload));
        log.info("Saga {} CREATE_PLAYLIST started for playlistId={} ownerId={}",
                state.getSagaId(), payload.playlistId(), payload.ownerId());
        send(RabbitConfig.MONGO_COMMANDS_QUEUE,
                new MongoCreatePlaylist(state.getSagaId(), payload));
        return state.getSagaId();
    }

    // --- Reply handling (drives the flow) ---

    @RabbitListener(queues = RabbitConfig.SAGA_REPLIES_QUEUE)
    public void onReply(SagaReply reply) {
        SagaState state = repository.findById(reply.sagaId()).orElse(null);
        if (state == null) {
            log.warn("Reply for unknown saga {} ignored", reply.sagaId());
            return;
        }
        // Idempotency: a saga that already finished must not react to a duplicate reply.
        if (state.getStatus().isTerminal()) {
            log.debug("Saga {} already {}; ignoring reply {}", state.getSagaId(), state.getStatus(), reply.operation());
            return;
        }

        log.info("Saga {} reply: participant={} op={} success={} reason={}",
                reply.sagaId(), reply.participant(), reply.operation(), reply.success(), reply.reason());

        switch (state.getSagaType()) {
            case PUBLISH_SONG -> handlePublish(state, reply);
            case DELETE_SONG -> handleDelete(state, reply);
            case RECORD_LISTEN -> handleRecordListen(state, reply);
            case CREATE_PLAYLIST -> handleCreatePlaylist(state, reply);
        }
    }

    private void handlePublish(SagaState state, SagaReply reply) {
        switch (state.getStatus()) {
            case STARTED -> { // reply is for the Mongo create step
                if (reply.success()) {
                    save(state, SagaStatus.MONGO_DONE);
                    send(RabbitConfig.GRAPH_COMMANDS_QUEUE,
                            new GraphCreateSong(state.getSagaId(), state.getPayload()));
                } else {
                    save(state, SagaStatus.FAILED);
                }
            }
            case MONGO_DONE -> { // the reply is for the Graph create step
                if (reply.success()) {
                    save(state, SagaStatus.COMPLETED);
                } else {
                    // Compensate the Mongo create by deleting the document, then fail.
                    save(state, SagaStatus.COMPENSATING);
                    send(RabbitConfig.MONGO_COMMANDS_QUEUE,
                            new MongoDeleteSong(state.getSagaId(), state.getPayload().getId()));
                    save(state, SagaStatus.FAILED);
                }
            }
            default -> log.warn("Saga {} unexpected status {} for PUBLISH_SONG reply", state.getSagaId(), state.getStatus());
        }
    }

    private void handleDelete(SagaState state, SagaReply reply) {
        switch (state.getStatus()) {
            case STARTED -> { // reply is for the Mongo delete step
                if (reply.success()) {
                    save(state, SagaStatus.MONGO_DONE);
                    send(RabbitConfig.GRAPH_COMMANDS_QUEUE,
                            new GraphDeleteSong(state.getSagaId(), state.getPayload().getId()));
                } else {
                    save(state, SagaStatus.FAILED);
                }
            }
            case MONGO_DONE -> { // reply is for the Graph delete step
                if (reply.success()) {
                    save(state, SagaStatus.COMPLETED);
                } else {
                    // Compensate the Mongo delete by re-creating from the saved snapshot, then fail.
                    save(state, SagaStatus.COMPENSATING);
                    send(RabbitConfig.MONGO_COMMANDS_QUEUE,
                            new MongoCreateSong(state.getSagaId(), state.getPayload()));
                    save(state, SagaStatus.FAILED);
                }
            }
            default -> log.warn("Saga {} unexpected status {} for DELETE_SONG reply", state.getSagaId(), state.getStatus());
        }
    }

    private void handleRecordListen(SagaState state, SagaReply reply) {
        ListenPayload listen = state.getListenPayload();
        switch (state.getStatus()) {
            case STARTED -> { // reply is for the Mongo record step
                if (reply.success()) {
                    save(state, SagaStatus.MONGO_DONE);
                    send(RabbitConfig.GRAPH_COMMANDS_QUEUE,
                            new GraphRecordListen(state.getSagaId(), listen.userId(), listen.songId()));
                } else {
                    save(state, SagaStatus.FAILED);
                }
            }
            case MONGO_DONE -> { // reply is for the Graph record step (terminal step)
                if (reply.success()) {
                    save(state, SagaStatus.COMPLETED);
                } else {
                    // Graph is terminal; compensate only Mongo (playCount--). Graph compensation
                    // (count=1 -> delete rel) is not needed here — see ListenGraphService javadoc.
                    save(state, SagaStatus.COMPENSATING);
                    send(RabbitConfig.MONGO_COMMANDS_QUEUE,
                            new MongoCompensateListen(state.getSagaId(), listen.userId(), listen.songId()));
                    save(state, SagaStatus.FAILED);
                }
            }
            default -> log.warn("Saga {} unexpected status {} for RECORD_LISTEN reply",
                    state.getSagaId(), state.getStatus());
        }
    }

    private void handleCreatePlaylist(SagaState state, SagaReply reply) {
        PlaylistPayload playlist = state.getPlaylistPayload();
        switch (state.getStatus()) {
            case STARTED -> { // reply is for the Mongo create step
                if (reply.success()) {
                    save(state, SagaStatus.MONGO_DONE);
                    send(RabbitConfig.GRAPH_COMMANDS_QUEUE,
                            new GraphCreatePlaylist(state.getSagaId(), playlist));
                } else {
                    save(state, SagaStatus.FAILED);
                }
            }
            case MONGO_DONE -> { // reply is for the Graph create step
                if (reply.success()) {
                    save(state, SagaStatus.COMPLETED);
                } else {
                    // Compensate the Mongo create by deleting the document, then fail.
                    save(state, SagaStatus.COMPENSATING);
                    send(RabbitConfig.MONGO_COMMANDS_QUEUE,
                            new MongoDeletePlaylist(state.getSagaId(), playlist.playlistId()));
                    save(state, SagaStatus.FAILED);
                }
            }
            default -> log.warn("Saga {} unexpected status {} for CREATE_PLAYLIST reply",
                    state.getSagaId(), state.getStatus());
        }
    }

    // --- Helpers ---

    private SagaState newSaga(SagaType type, Song payload) {
        return repository.save(SagaState.start(type, payload));
    }

    private SagaState newSagaListen(ListenPayload listenPayload) {
        return repository.save(SagaState.startListen(listenPayload));
    }

    private void save(SagaState state, SagaStatus status) {
        state.updateStatus(status);
        repository.save(state);
    }

    private void send(String routingKey, Object command) {
        // Default exchange is "saga.exchange" (set on the RabbitTemplate); routing key == queue name.
        rabbitTemplate.convertAndSend(routingKey, command);
    }
}
