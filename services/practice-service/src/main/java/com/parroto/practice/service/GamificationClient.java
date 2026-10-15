package com.parroto.practice.service;

import com.parroto.common.security.InternalTokenFilter;
import com.parroto.practice.entity.PracticeAttempt;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P2-B: reports a saved dictation/shadowing attempt to vocabulary-service
 * ({@code POST /internal/gamification/events}, X-Internal-Token), which turns it into XP.
 * <p>
 * Fire-and-forget: the call runs on a small background pool after the attempt's transaction has
 * committed (immediately when there is none), with short timeouts. It never throws and never delays
 * the attempt; when vocabulary-service is down or the queue is full the event is dropped with a WARN.
 * vocabulary-service is idempotent, so a duplicate delivery is harmless.
 */
@Service
public class GamificationClient {

    private static final Logger log = LoggerFactory.getLogger(GamificationClient.class);
    static final String PATH = "/internal/gamification/events";
    private static final int QUEUE_CAPACITY = 500;

    private final RestTemplate restTemplate;
    private final String eventsUrl;
    private final String internalToken;
    private final ExecutorService executor;

    @Autowired
    public GamificationClient(RestTemplateBuilder builder,
                              @Value("${services.vocabulary-service-url:}") String vocabularyServiceUrl,
                              @Value("${internal.token:}") String internalToken) {
        this(builder.setConnectTimeout(Duration.ofSeconds(1)).setReadTimeout(Duration.ofSeconds(2)).build(),
                vocabularyServiceUrl, internalToken, newExecutor());
    }

    GamificationClient(RestTemplate restTemplate, String vocabularyServiceUrl, String internalToken,
                       ExecutorService executor) {
        this.restTemplate = restTemplate;
        this.eventsUrl = vocabularyServiceUrl == null || vocabularyServiceUrl.isBlank()
                ? null : vocabularyServiceUrl.replaceAll("/+$", "") + PATH;
        this.internalToken = internalToken;
        this.executor = executor;
        if (eventsUrl == null) {
            log.warn("VOCABULARY_SERVICE_URL is not set: practice attempts will not earn XP");
        }
    }

    private static ExecutorService newExecutor() {
        AtomicInteger n = new AtomicInteger();
        return new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(QUEUE_CAPACITY), r -> {
            Thread t = new Thread(r, "gamification-events-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, (r, pool) -> log.warn("Gamification event queue full; event dropped"));
    }

    /** Queues the XP event of a saved attempt (after commit when called inside a transaction). */
    public void attemptSaved(PracticeAttempt attempt) {
        if (eventsUrl == null || attempt == null) {
            return;
        }
        try {
            Map<String, Object> body = body(attempt);
            Runnable send = () -> send(body);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submit(send);
                    }
                });
            } else {
                submit(send);
            }
        } catch (RuntimeException e) {
            log.warn("Could not queue gamification event for attempt {}: {}", attempt.getId(), e.getMessage());
        }
    }

    static Map<String, Object> body(PracticeAttempt attempt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", attempt.getUserId());
        body.put("type", "shadowing".equalsIgnoreCase(attempt.getMode()) ? "SHADOWING" : "DICTATION");
        body.put("attemptId", attempt.getId());
        body.put("lessonId", attempt.getLessonId());
        body.put("sentenceId", attempt.getSentenceId());
        body.put("accuracy", attempt.getAccuracy() == null ? null : attempt.getAccuracy().doubleValue());
        body.put("score", attempt.getScore() == null ? null : attempt.getScore().doubleValue());
        body.put("occurredAt", (attempt.getCreatedAt() != null ? attempt.getCreatedAt() : OffsetDateTime.now()).toString());
        return body;
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RuntimeException e) {
            log.warn("Gamification event not sent: {}", e.getMessage());
        }
    }

    private void send(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalTokenFilter.HEADER, internalToken);
        try {
            restTemplate.postForEntity(eventsUrl, new HttpEntity<>(body, headers), Map.class);
        } catch (RuntimeException e) {
            log.warn("Gamification event for attempt {} not delivered (vocabulary-service): {}",
                    body.get("attemptId"), e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
