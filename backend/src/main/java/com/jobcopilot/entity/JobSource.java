package com.jobcopilot.entity;

import com.jobcopilot.entity.enums.JobSourceType;
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

/**
 * Configuration for an external job provider.
 */
@Entity
@Table(name = "job_source")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSource extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobSourceType type;

    /** JSON provider-specific configuration. */
    @Column(columnDefinition = "TEXT")
    private String configuration;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
