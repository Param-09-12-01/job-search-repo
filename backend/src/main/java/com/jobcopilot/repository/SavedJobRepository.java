package com.jobcopilot.repository;

import com.jobcopilot.entity.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    Optional<SavedJob> findByPostingId(Long postingId);

    boolean existsByPostingId(Long postingId);

    void deleteByPostingId(Long postingId);
}
