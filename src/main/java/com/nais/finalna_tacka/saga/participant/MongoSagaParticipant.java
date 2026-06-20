package com.nais.finalna_tacka.saga.participant;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.saga.messages.MongoCreateSong;
import com.nais.finalna_tacka.saga.messages.MongoDeleteSong;
import com.nais.finalna_tacka.saga.messages.SagaReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Mongo participant: consumes commands from "mongo.commands" and replies to "saga.replies".
 *
 * <p>Class-level {@code @RabbitListener} + {@code @RabbitHandler} dispatches each message to
 * the handler matching its payload type (the JSON converter carries the type in a header),
 * so create and delete commands on the one queue go to the right method.</p>
 *
 * <p>Consumers must be idempotent (RabbitMQ delivers at least once): a redelivered create
 * should not duplicate, a redelivered delete should be a no-op if already gone.</p>
 */
@Component
@RabbitListener(queues = RabbitConfig.MONGO_COMMANDS_QUEUE)
public class MongoSagaParticipant {

    private static final Logger log = LoggerFactory.getLogger(MongoSagaParticipant.class);
    private static final String PARTICIPANT = "mongo";

    private final RabbitTemplate rabbitTemplate;

    public MongoSagaParticipant(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitHandler
    public void onCreate(MongoCreateSong cmd) {
        try {
            // TODO (Member A): perform the Mongo insert here for MongoCreateSong.
            // Idempotency: upsert by id so a redelivered command does not create a duplicate.
            log.info("Saga {} MongoCreateSong received (not yet implemented)", cmd.sagaId());
            reply(cmd.sagaId(), "MongoCreateSong", true, null);
        } catch (Exception e) {
            reply(cmd.sagaId(), "MongoCreateSong", false, e.getMessage());
        }
    }

    @RabbitHandler
    public void onDelete(MongoDeleteSong cmd) {
        try {
            // TODO (Member B): perform the Mongo delete + remove-from-playlists here for MongoDeleteSong.
            // Idempotency: deleting an already-missing song should succeed (no-op).
            log.info("Saga {} MongoDeleteSong received (not yet implemented)", cmd.sagaId());
            reply(cmd.sagaId(), "MongoDeleteSong", true, null);
        } catch (Exception e) {
            reply(cmd.sagaId(), "MongoDeleteSong", false, e.getMessage());
        }
    }

    private void reply(String sagaId, String operation, boolean success, String reason) {
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_REPLIES_QUEUE,
                new SagaReply(sagaId, PARTICIPANT, operation, success, reason));
    }
}
