package com.jobcopilot.repository;

import com.jobcopilot.entity.JobSource;
import com.jobcopilot.entity.enums.JobSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobSourceRepository extends JpaRepository<JobSource, Long> {

    List<JobSource> findByEnabledTrue();

    Optional<JobSource> findByType(JobSourceType type);

    Optional<JobSource> findByName(String name);
}
