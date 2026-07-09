package com.jobcopilot.repository;

import com.jobcopilot.entity.DismissedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DismissedJobRepository extends JpaRepository<DismissedJob, Long> {

    boolean existsByPostingId(Long postingId);

    @Query("select d.posting.id from DismissedJob d")
    List<Long> findAllDismissedPostingIds();
}
