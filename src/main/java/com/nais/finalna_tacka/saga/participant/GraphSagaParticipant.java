package com.nais.finalna_tacka.saga.participant;

import com.nais.finalna_tacka.config.RabbitConfig;
import com.nais.finalna_tacka.saga.messages.GraphCreateSong;
import com.nais.finalna_tacka.saga.messages.GraphDeleteSong;
import com.nais.finalna_tacka.saga.messages.GraphRecordListen;
import com.nais.finalna_tacka.service.ListenGraphService;
import com.nais.finalna_tacka.service.SongGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Neo4j participant: prima komande sa "graph.commands" i šalje odgovore na "saga.replies"
 * (preko {@link SagaReplyPublisher}).
 *
 * <p>{@code @RabbitListener} na nivou klase + {@code @RabbitHandler} prosleđuje svaku poruku
 * handleru koji odgovara tipu njenog payload-a (JSON konverter nosi tip u headeru), tako da
 * create i delete komande sa istog queue-a idu u pravu metodu.</p>
 *
 * <p>Konzumenti moraju biti idempotentni (RabbitMQ isporučuje barem jednom): koristi MERGE
 * create-ove i tretiraj brisanje nepostojećeg čvora kao no-op.</p>
 */
@Component
@RequiredArgsConstructor
@RabbitListener(queues = RabbitConfig.GRAPH_COMMANDS_QUEUE)
public class GraphSagaParticipant {

    private static final String PARTICIPANT = "graph";

    private final SongGraphService songGraphService;
    private final ListenGraphService listenGraphService;
    private final SagaReplyPublisher replyPublisher;

    @RabbitHandler
    public void onCreate(GraphCreateSong cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "create",
                () -> songGraphService.createSong(cmd.payload()));
    }

    @RabbitHandler
    public void onDelete(GraphDeleteSong cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "delete",
                () -> songGraphService.deleteSong(cmd.songId()));
    }

    @RabbitHandler
    public void onRecordListen(GraphRecordListen cmd) {
        replyPublisher.runAndReply(cmd.sagaId(), PARTICIPANT, "recordListen",
                () -> listenGraphService.recordListen(cmd.userId(), cmd.songId()));
    }
}
