package com.parroto.vocabulary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** Per-user game progress (T16): XP and the daily points goal. */
@Entity
@Table(name = "game_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameProfile {

    public static final int DEFAULT_DAILY_GOAL = 200;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Builder.Default
    private Long xp = 0L;

    @Column(name = "daily_goal")
    @Builder.Default
    private Integer dailyGoal = DEFAULT_DAILY_GOAL;

    /** Last day reported to practice-service as a met goal (streak). */
    @Column(name = "goal_recorded_day")
    private LocalDate goalRecordedDay;
}
