package com.parroto.practice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderSelectionTest {

    @Test
    void autoStorageUsesS3OnlyWithRealKeys() {
        assertThat(ProviderSelection.resolveStorage("auto", "AKIAREAL", "secret")).isEqualTo("s3");
        assertThat(ProviderSelection.resolveStorage("auto", "", "secret")).isEqualTo("local");
        assertThat(ProviderSelection.resolveStorage("auto", "AKIAREAL", null)).isEqualTo("local");
        assertThat(ProviderSelection.resolveStorage("auto", "your_aws_access_key", "your_secret")).isEqualTo("local");
        assertThat(ProviderSelection.resolveStorage(null, null, null)).isEqualTo("local");
        assertThat(ProviderSelection.resolveStorage("LOCAL", "AKIAREAL", "secret")).isEqualTo("local");
    }

    @Test
    void explicitS3WithoutKeysFailsFast() {
        assertThatThrownBy(() -> ProviderSelection.resolveStorage("s3", "", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProviderSelection.resolveStorage("ftp", "a", "b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void autoSpeechUsesAzureOnlyWithRealKey() {
        assertThat(ProviderSelection.resolveSpeech("auto", "abc123")).isEqualTo("azure");
        assertThat(ProviderSelection.resolveSpeech("auto", "")).isEqualTo("mock");
        assertThat(ProviderSelection.resolveSpeech("auto", "your_azure_key")).isEqualTo("mock");
        assertThat(ProviderSelection.resolveSpeech("mock", "abc123")).isEqualTo("mock");
        assertThatThrownBy(() -> ProviderSelection.resolveSpeech("azure", " "))
                .isInstanceOf(IllegalStateException.class);
    }
}
