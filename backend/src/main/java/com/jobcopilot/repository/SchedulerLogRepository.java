package com.jobcopilot.repository;

import com.jobcopilot.entity.SchedulerLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchedulerLogRepository extends JpaRepository<SchedulerLog, Long> {

    List<SchedulerLog> findTop10ByOrderByStartedAtDesc();

    Page<SchedulerLog> findAllByOrderByStartedAtDesc(Pageable pageable);
}
