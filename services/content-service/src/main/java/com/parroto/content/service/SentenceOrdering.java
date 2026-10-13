package com.parroto.content.service;

import com.parroto.content.entity.Sentence;

import java.util.ArrayList;
import java.util.List;

/** Pure helpers keeping {@code sentence_order} contiguous (1..N) within a lesson. */
final class SentenceOrdering {

    private SentenceOrdering() {
    }

    /** Assigns 1..N following the list order; returns the same list. */
    static List<Sentence> renumber(List<Sentence> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setSentenceOrder(i + 1);
        }
        return ordered;
    }

    /** Copy of {@code ordered} without {@code target}, renumbered 1..N-1. */
    static List<Sentence> without(List<Sentence> ordered, Sentence target) {
        List<Sentence> remaining = new ArrayList<>(ordered);
        remaining.remove(target);
        return renumber(remaining);
    }

    /** Copy of {@code ordered} with {@code target} moved to position {@code newOrder} (clamped to 1..N), renumbered. */
    static List<Sentence> move(List<Sentence> ordered, Sentence target, int newOrder) {
        List<Sentence> result = new ArrayList<>(ordered);
        result.remove(target);
        int index = Math.max(0, Math.min(newOrder - 1, result.size()));
        result.add(index, target);
        return renumber(result);
    }
}
