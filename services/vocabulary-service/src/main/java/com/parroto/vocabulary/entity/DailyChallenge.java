package com.parroto.vocabulary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** The 10 cards of one day's challenge, identical for every player (T16). */
@Entity
@Table(name = "daily_challenge")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyChallenge {

    @Id
    private LocalDate day;

    /** Comma-separated card ids, in question order. */
    @Column(name = "card_ids", nullable = false, columnDefinition = "text")
    private String cardIds;
}
