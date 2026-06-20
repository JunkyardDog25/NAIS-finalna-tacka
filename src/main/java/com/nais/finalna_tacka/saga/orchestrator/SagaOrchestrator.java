package com.nais.finalna_tacka.saga.orchestrator;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.domain.mongo.Song;
import com.nais.finalna_tacka.repository.mongo.SagaStateRepository;
import com.nais.finalna_tacka.saga.messages.GraphCreateSong;
import com.nais.finalna_tacka.saga.messages.GraphDeleteSong;
import com.nais.finalna_tacka.saga.messages.MongoCreateSong;
import com.nais.finalna_tacka.saga.messages.MongoDeleteSong;
import com.nais.finalna_tacka.saga.messages.SagaReply;
import com.nais.finalna_tacka.saga.state.SagaState;
import com.nais.finalna_tacka.saga.state.SagaStatus;
import com.nais.finalna_tacka.saga.state.SagaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga ORCHESTRATOR. Owns the flow: it starts a saga, sends the first command, and reacts
 * to each {@link SagaReply} by sending the next command or a compensation, persisting the
 * status after every transition.
 *
 * <p>Sequence (happy path PUBLISH_SONG):</p>
 * <pre>
 *   start -> [STARTED] --MongoCreateSong--> mongo --reply ok--> [MONGO_DONE]
 *         --GraphCreateSong--> graph --reply ok--> [COMPLETED]
 * </pre>
 * <p>If the graph step fails the orchestrator compensates the Mongo step and ends FAILED.
 * Replies for a saga already in a terminal state (COMPLETED/FAILED) are ignored so the
 * handler stays idempotent under RabbitMQ's at-least-once delivery.</p>
 */
@Component
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final RabbitTemplate rabbitTemplate;
    private final SagaStateRepository repository;

    public SagaOrchestrator(RabbitTemplate rabbitTemplate, SagaStateRepository repository) {
        this.rabbitTemplate = rabbitTemplate;
        this.repository = repository;
    }

    // --- Entry points (called by the REST controller) ---

    /** Begin a PUBLISH_SONG saga: write to Mongo first, then mirror into Neo4j. */
    public String startPublishSong(Song song) {
        SagaState state = newSaga(SagaType.PUBLISH_SONG, song);
        log.info("Saga {} PUBLISH_SONG started", state.getSagaId());
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

    // --- Reply handling (drives the flow) ---

    @RabbitListener(queues = RabbitConfig.SAGA_REPLIES_QUEUE)
    public void onReply(SagaReply reply) {
        SagaState state = repository.findById(reply.sagaId()).orElse(null);
        if (state == null) {
            log.warn("Reply for unknown saga {} ignored", reply.sagaId());
            return;
        }
        // Idempotency: a saga that already finished must not react to a duplicate reply.
        if (isTerminal(state.getStatus())) {
            log.debug("Saga {} already {}; ignoring reply {}", state.getSagaId(), state.getStatus(), reply.operation());
            return;
        }

        log.info("Saga {} reply: participant={} op={} success={} reason={}",
                reply.sagaId(), reply.participant(), reply.operation(), reply.success(), reply.reason());

        switch (state.getSagaType()) {
            case PUBLISH_SONG -> handlePublish(state, reply);
            case DELETE_SONG -> handleDelete(state, reply);
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
            case MONGO_DONE -> { // reply is for the Graph create step
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

    // --- Helpers ---

    private SagaState newSaga(SagaType type, Song payload) {
        Instant now = Instant.now();
        SagaState state = new SagaState();
        state.setSagaId(UUID.randomUUID().toString());
        state.setSagaType(type);
        state.setStatus(SagaStatus.STARTED);
        state.setPayload(payload);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        return repository.save(state);
    }

    private void save(SagaState state, SagaStatus status) {
        state.setStatus(status);
        state.setUpdatedAt(Instant.now());
        repository.save(state);
    }

    private void send(String routingKey, Object command) {
        // Default exchange is "saga.exchange" (set on the RabbitTemplate); routing key == queue name.
        rabbitTemplate.convertAndSend(routingKey, command);
    }

    private boolean isTerminal(SagaStatus status) {
        return status == SagaStatus.COMPLETED || status == SagaStatus.FAILED;
    }
}
