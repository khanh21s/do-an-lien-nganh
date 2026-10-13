package com.parroto.practice.config;

import com.parroto.practice.service.speech.PronunciationScorer;
import com.parroto.practice.service.storage.RecordingStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Logs the resolved storage / speech backends once at startup (architecture §8.1). */
@Component
public class ProviderStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(ProviderStartupLogger.class);

    private final RecordingStorage recordingStorage;
    private final PronunciationScorer pronunciationScorer;

    public ProviderStartupLogger(RecordingStorage recordingStorage, PronunciationScorer pronunciationScorer) {
        this.recordingStorage = recordingStorage;
        this.pronunciationScorer = pronunciationScorer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logProviders() {
        log.info("Storage provider: {} | Speech provider: {}", recordingStorage.describe(), pronunciationScorer.name());
    }
}
