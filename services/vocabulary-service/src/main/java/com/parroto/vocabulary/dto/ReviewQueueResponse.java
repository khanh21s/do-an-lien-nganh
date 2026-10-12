package com.parroto.vocabulary.dto;

import java.util.List;

public record ReviewQueueResponse(List<ReviewQueueItem> queue, long totalDueCards, long totalCards) {
}
