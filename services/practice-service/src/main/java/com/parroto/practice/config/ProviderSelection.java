package com.parroto.practice.config;

import java.util.Locale;

/**
 * Resolves the STORAGE_PROVIDER / SPEECH_PROVIDER settings (architecture §8.1):
 * {@code auto} picks the real backend only when its credentials look real, otherwise the
 * local/mock fallback; an explicit {@code s3}/{@code azure} without credentials fails fast.
 */
public final class ProviderSelection {

    public static final String STORAGE_S3 = "s3";
    public static final String STORAGE_LOCAL = "local";
    public static final String SPEECH_AZURE = "azure";
    public static final String SPEECH_MOCK = "mock";

    private ProviderSelection() {
    }

    /** A credential counts as configured when non-blank and not a ".env.example" placeholder ("your_..."). */
    public static boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !value.trim().toLowerCase(Locale.ROOT).startsWith("your_");
    }

    public static String resolveStorage(String provider, String accessKey, String secretKey) {
        boolean hasKeys = isConfigured(accessKey) && isConfigured(secretKey);
        String p = normalize(provider);
        return switch (p) {
            case "auto" -> hasKeys ? STORAGE_S3 : STORAGE_LOCAL;
            case STORAGE_LOCAL -> STORAGE_LOCAL;
            case STORAGE_S3 -> {
                if (!hasKeys) {
                    throw new IllegalStateException(
                            "STORAGE_PROVIDER=s3 but AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY are missing");
                }
                yield STORAGE_S3;
            }
            default -> throw new IllegalStateException(
                    "Unknown STORAGE_PROVIDER '" + provider + "' (expected auto, s3 or local)");
        };
    }

    public static String resolveSpeech(String provider, String speechKey) {
        boolean hasKey = isConfigured(speechKey);
        String p = normalize(provider);
        return switch (p) {
            case "auto" -> hasKey ? SPEECH_AZURE : SPEECH_MOCK;
            case SPEECH_MOCK -> SPEECH_MOCK;
            case SPEECH_AZURE -> {
                if (!hasKey) {
                    throw new IllegalStateException("SPEECH_PROVIDER=azure but AZURE_SPEECH_KEY is missing");
                }
                yield SPEECH_AZURE;
            }
            default -> throw new IllegalStateException(
                    "Unknown SPEECH_PROVIDER '" + provider + "' (expected auto, azure or mock)");
        };
    }

    private static String normalize(String provider) {
        return provider == null || provider.isBlank() ? "auto" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
