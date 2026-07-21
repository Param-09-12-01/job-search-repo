package com.jobcopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "source_fetch_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceFetchState {

    @Id
    @Column(length = 50)
    private String source;

    @Column(name = "last_posted_at", nullable = false)
    private Instant lastPostedAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
