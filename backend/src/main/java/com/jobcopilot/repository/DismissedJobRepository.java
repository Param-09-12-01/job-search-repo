package com.jobcopilot.repository;

import com.jobcopilot.entity.DismissedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface DismissedJobRepository extends JpaRepository<DismissedJob, Long> {

    boolean existsByPostingId(Long postingId);

    @Query("select d.posting.id from DismissedJob d")
    List<Long> findAllDismissedPostingIds();

    @Query("SELECT d.posting.id FROM DismissedJob d WHERE d.posting.id IN :ids")
    Set<Long> findDismissedPostingIds(@Param("ids") List<Long> ids);
}
