package com.parroto.practice.dto;

public record WordScoreDto(int position, String word, boolean isCorrect, String expected) {
}
