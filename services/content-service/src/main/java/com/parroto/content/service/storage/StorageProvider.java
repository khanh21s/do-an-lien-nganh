package com.parroto.content.service.storage;

import java.util.Locale;

/** Resolution of {@code STORAGE_PROVIDER} (architecture §8.1). */
public enum StorageProvider {
    S3, LOCAL;

    /**
     * {@code auto} → S3 only when both AWS keys are non-blank and not placeholders ({@code your_...});
     * otherwise LOCAL. An explicit {@code s3} without keys fails fast.
     */
    public static StorageProvider resolve(String configured, String accessKey, String secretKey) {
        String value = configured == null || configured.isBlank() ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        boolean hasKeys = isRealKey(accessKey) && isRealKey(secretKey);
        return switch (value) {
            case "auto" -> hasKeys ? S3 : LOCAL;
            case "local" -> LOCAL;
            case "s3" -> {
                if (!hasKeys) {
                    throw new IllegalStateException(
                            "STORAGE_PROVIDER=s3 but AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY are missing");
                }
                yield S3;
            }
            default -> throw new IllegalStateException(
                    "Unknown STORAGE_PROVIDER '" + configured + "' (expected auto, s3 or local)");
        };
    }

    static boolean isRealKey(String key) {
        return key != null && !key.isBlank() && !key.trim().startsWith("your_");
    }
}
