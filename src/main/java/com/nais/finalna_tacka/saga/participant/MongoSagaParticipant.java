package com.nais.finalna_tacka.saga.participant;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.saga.messages.MongoCompensateListen;
import com.nais.finalna_tacka.saga.messages.MongoCreateSong;
import com.nais.finalna_tacka.saga.messages.MongoDeleteSong;
import com.nais.finalna_tacka.saga.messages.MongoRecordListen;
import com.nais.finalna_tacka.service.ListenMongoService;
import com.nais.finalna_tacka.service.SongMongoService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Mongo participant: prima komande sa "mongo.commands" i šalje odgovore na "saga.replies"
 * (preko {@link SagaReplyPublisher}).
 *
 * <p>{@code @RabbitListener} na nivou klase + {@code @RabbitHandler} prosleđuje svaku poruku
 * handleru koji odgovara tipu njenog payload-a (JSON konverter nosi tip u headeru), tako da
 * create i delete komande sa istog queue-a idu u pravu metodu.</p>
 *
 * <p>Konzumenti moraju biti idempotentni (RabbitMQ isporučuje barem jednom): ponovljeni create
 * ne sme da duplira, ponovljeni delete treba da bude no-op ako je već obrisano.</p>
 */
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitConfig.MONGO_COMMANDS_QUEUE)
public class MongoSagaParticipant {

    private static final String PARTICIPANT = "mongo";

    private final SongMongoService songMongoService;
    private final ListenMongoService listenMongoService;
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

    @RabbitHandler
    public void onRecordListen(MongoRecordListen cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "recordListen",
                () -> listenMongoService.recordListen(cmd.userId(), cmd.songId()));
    }

    @RabbitHandler
    public void onCompensateListen(MongoCompensateListen cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "compensateListen",
                () -> listenMongoService.compensateListen(cmd.userId(), cmd.songId()));
    }
}
