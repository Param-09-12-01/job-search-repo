package com.jobcopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.LocalDateTime;

/**
 * A normalized job posting produced by a {@code JobSourceAdapter}.
 *
 * <p>De-duplication is enforced two ways: a unique {@code (source, externalId)} pair and a
 * content {@code fingerprint} (sha-256 of title + company + location).</p>
 */
@Entity
@Table(name = "posting")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "external_id", nullable = false, length = 1024)
    private String externalId;

    @Column(nullable = false, length = 512)
    private String title;

    @Column
    private String company;

    @Column
    private String location;

    @Column(nullable = false)
    @Builder.Default
    private boolean remote = false;

    @Column(length = 120)
    private String salary;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(nullable = false)
    @Builder.Default
    private int score = 0;

    @Column(nullable = false, columnDefinition = "CHAR(64)")
    private String fingerprint;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "scheduler_run_id")
    private Long schedulerRunId;
}
