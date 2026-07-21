package com.jobcopilot.repository;

import com.jobcopilot.entity.Posting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostingRepository extends JpaRepository<Posting, Long>, JpaSpecificationExecutor<Posting> {

    boolean existsByFingerprint(String fingerprint);

    boolean existsBySourceAndExternalId(String source, String externalId);

    Optional<Posting> findByFingerprint(String fingerprint);

    @Modifying
    @Query("UPDATE Posting p SET p.archived = true WHERE p.archived = false AND p.postedAt IS NOT NULL AND p.postedAt < :cutoff")
    int archiveOlderThan(LocalDateTime cutoff);

    List<Posting> findBySchedulerRunIdOrderByScoreDesc(Long schedulerRunId);
}
