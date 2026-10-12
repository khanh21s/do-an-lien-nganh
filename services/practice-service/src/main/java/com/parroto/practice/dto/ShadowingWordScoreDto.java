package com.parroto.practice.dto;

import java.util.List;

public record ShadowingWordScoreDto(String word, List<PhonemeScoreDto> phonemes, double wordAccuracy, String status) {
}
