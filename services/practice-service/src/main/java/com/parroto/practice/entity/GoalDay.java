package com.parroto.practice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** T16: a day on which the user met a daily goal outside dictation/shadowing; counts for the streak. */
@Entity
@Table(name = "goal_day")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalDay {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private String source;
}
