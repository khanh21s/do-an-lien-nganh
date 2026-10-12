package com.parroto.subscription.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(nullable = false)
    private Long price;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode features;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
