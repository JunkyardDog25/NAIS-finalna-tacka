package com.nais.finalna_tacka.saga.participant;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.saga.messages.MongoCreateSong;
import com.nais.finalna_tacka.saga.messages.MongoDeleteSong;
import com.nais.finalna_tacka.saga.messages.SagaReply;
import com.nais.finalna_tacka.service.SongMongoService;
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
    private final SongMongoService songMongoService;

    public MongoSagaParticipant(RabbitTemplate rabbitTemplate, SongMongoService songMongoService) {
        this.rabbitTemplate = rabbitTemplate;
        this.songMongoService = songMongoService;
    }

    @RabbitHandler
    public void onCreate(MongoCreateSong cmd) {
        try {
            songMongoService.createSong(cmd.payload());
            reply(cmd.sagaId(), "create", true, null);
        } catch (Exception e) {
            log.error("Saga {} MongoCreateSong failed: {}", cmd.sagaId(), e.getMessage());
            reply(cmd.sagaId(), "create", false, e.getMessage());
        }
    }

    @RabbitHandler
    public void onDelete(MongoDeleteSong cmd) {
        try {
            songMongoService.deleteSong(cmd.songId());
            reply(cmd.sagaId(), "delete", true, null);
        } catch (Exception e) {
            log.error("Saga {} MongoDeleteSong failed: {}", cmd.sagaId(), e.getMessage());
            reply(cmd.sagaId(), "delete", false, e.getMessage());
        }
    }

    private void reply(String sagaId, String operation, boolean success, String reason) {
        rabbitTemplate.convertAndSend(RabbitConfig.SAGA_REPLIES_QUEUE,
                new SagaReply(sagaId, PARTICIPANT, operation, success, reason));
    }
}
