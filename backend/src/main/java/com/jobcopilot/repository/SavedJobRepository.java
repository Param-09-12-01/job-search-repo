package com.jobcopilot.repository;

import com.jobcopilot.entity.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

    Optional<SavedJob> findByPostingId(Long postingId);

    boolean existsByPostingId(Long postingId);

    void deleteByPostingId(Long postingId);

    @Query("SELECT s.posting.id FROM SavedJob s WHERE s.posting.id IN :ids")
    Set<Long> findSavedPostingIds(@Param("ids") List<Long> ids);
}
