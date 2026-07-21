package com.jobcopilot.entity;

import com.jobcopilot.entity.enums.SchedulerRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Records a single background scheduler run with its outcome and counters.
 */
@Entity
@Table(name = "scheduler_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SchedulerRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "fetched_count", nullable = false)
    @Builder.Default
    private int fetchedCount = 0;

    @Column(name = "new_count", nullable = false)
    @Builder.Default
    private int newCount = 0;

    @Column(name = "duplicate_count", nullable = false)
    @Builder.Default
    private int duplicateCount = 0;

    @Column(name = "notified_count", nullable = false)
    @Builder.Default
    private int notifiedCount = 0;

    @Column(columnDefinition = "TEXT")
    private String message;
}
