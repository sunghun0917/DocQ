package com.ssafy.docq_backend.domain.game.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameEventPoller {
    private final GameEventStore gameEventStore;
    private final GameEventProcessor gameEventProcessor;

    @Scheduled(fixedDelayString = "${game.events.poll-interval-ms:200}")
    public void poll() {
        List<GameEvent> events = gameEventStore.popDueEvents(20);
        for (GameEvent event : events) {
            try {
                gameEventProcessor.handle(event);
                gameEventStore.markProcessed(event.getId());
            } catch (Exception e) {
                log.error("Failed to process event {}", event.getId(), e);
                long backoffMs = 500L + (event.getAttempt() * 500L);
                gameEventStore.reschedule(event, backoffMs);
            }
        }
    }
}
