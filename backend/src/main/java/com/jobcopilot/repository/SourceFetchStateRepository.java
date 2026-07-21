package com.jobcopilot.repository;

import com.jobcopilot.entity.SourceFetchState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceFetchStateRepository extends JpaRepository<SourceFetchState, String> {
}
