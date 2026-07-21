package com.jobcopilot.repository;

import com.jobcopilot.entity.Application;
import com.jobcopilot.entity.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByPostingId(Long postingId);

    boolean existsByPostingId(Long postingId);

    @Query("SELECT a.posting.id FROM Application a WHERE a.posting.id IN :ids")
    Set<Long> findAppliedPostingIds(@Param("ids") List<Long> ids);

    List<Application> findByStatus(ApplicationStatus status);

    long countByStatus(ApplicationStatus status);

    @Query("select a.status as status, count(a) as count from Application a group by a.status")
    List<StatusCount> countGroupedByStatus();

    /** Projection for status aggregation. */
    interface StatusCount {
        ApplicationStatus getStatus();
        long getCount();
    }
}
