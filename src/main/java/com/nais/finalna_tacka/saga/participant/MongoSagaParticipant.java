package com.nais.finalna_tacka.saga.participant;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.saga.messages.MongoCreateSong;
import com.nais.finalna_tacka.saga.messages.MongoDeleteSong;
import com.nais.finalna_tacka.service.SongMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Mongo participant: consumes commands from "mongo.commands" and replies to "saga.replies"
 * (via {@link SagaReplyPublisher}).
 *
 * <p>Class-level {@code @RabbitListener} + {@code @RabbitHandler} dispatches each message to
 * the handler matching its payload type (the JSON converter carries the type in a header),
 * so create and delete commands on the one queue go to the right method.</p>
 *
 * <p>Consumers must be idempotent (RabbitMQ delivers at least once): a redelivered create
 * should not duplicate, a redelivered delete should be a no-op if already gone.</p>
 */
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitConfig.MONGO_COMMANDS_QUEUE)
public class MongoSagaParticipant {

    private static final String PARTICIPANT = "mongo";

    private final SongMongoService songMongoService;
    private final SagaReplyPublisher replyPublisher;

    @RabbitHandler
    public void onCreate(MongoCreateSong cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "create",
                () -> songMongoService.createSong(cmd.payload()));
    }

    @RabbitHandler
    public void onDelete(MongoDeleteSong cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "delete",
                () -> songMongoService.deleteSong(cmd.songId()));
    }
}
