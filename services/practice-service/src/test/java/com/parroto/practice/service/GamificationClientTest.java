package com.parroto.practice.service;

import com.parroto.practice.entity.PracticeAttempt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GamificationClientTest {

    /** Runs tasks inline, or rejects them all (queue full). */
    static final class InlineExecutor extends AbstractExecutorService {
        boolean reject;

        @Override
        public void execute(Runnable command) {
            if (reject) throw new RejectedExecutionException("full");
            command.run();
        }

        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }

    private final RestTemplate rest = mock(RestTemplate.class);
    private final InlineExecutor executor = new InlineExecutor();
    private final GamificationClient client = new GamificationClient(rest, "http://vocabulary-service:8004/", "tok", executor);

    private PracticeAttempt attempt(String mode) {
        return PracticeAttempt.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).lessonId(UUID.randomUUID())
                .sentenceId(UUID.randomUUID()).mode(mode).accuracy(new BigDecimal("100.00")).score(new BigDecimal("80.00"))
                .createdAt(OffsetDateTime.parse("2026-10-15T09:00:00+07:00")).build();
    }

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void postsTheAttemptWithTheInternalToken() {
        PracticeAttempt a = attempt("dictation");
        client.attemptSaved(a);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(rest).postForEntity(eq("http://vocabulary-service:8004/internal/gamification/events"), captor.capture(), eq(Map.class));
        HttpEntity<Map<String, Object>> sent = captor.getValue();
        assertThat(sent.getHeaders().getFirst("X-Internal-Token")).isEqualTo("tok");
        assertThat(sent.getBody()).containsEntry("type", "DICTATION").containsEntry("userId", a.getUserId())
                .containsEntry("sentenceId", a.getSentenceId()).containsEntry("attemptId", a.getId())
                .containsEntry("accuracy", 100.0).containsEntry("score", 80.0)
                .containsEntry("occurredAt", "2026-10-15T09:00+07:00");
        assertThat(GamificationClient.body(attempt("shadowing"))).containsEntry("type", "SHADOWING");
    }

    @Test
    void insideATransactionItSendsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        client.attemptSaved(attempt("dictation"));
        verifyNoInteractions(rest);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(rest).postForEntity(any(String.class), any(), eq(Map.class));
    }

    @Test
    void neverThrowsWhenVocabularyServiceIsDownOrTheQueueIsFull() {
        when(rest.postForEntity(any(String.class), any(), eq(Map.class))).thenThrow(new ResourceAccessException("Connection refused"));
        assertThatCode(() -> client.attemptSaved(attempt("dictation"))).doesNotThrowAnyException();

        executor.reject = true;
        assertThatCode(() -> client.attemptSaved(attempt("shadowing"))).doesNotThrowAnyException();
    }

    @Test
    void disabledWithoutAUrl() {
        GamificationClient off = new GamificationClient(rest, " ", "tok", executor);
        off.attemptSaved(attempt("dictation"));
        verifyNoInteractions(rest);
    }
}
