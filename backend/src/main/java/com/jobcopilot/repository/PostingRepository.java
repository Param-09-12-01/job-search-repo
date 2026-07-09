package com.jobcopilot.repository;

import com.jobcopilot.entity.Posting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostingRepository extends JpaRepository<Posting, Long>, JpaSpecificationExecutor<Posting> {

    boolean existsByFingerprint(String fingerprint);

    boolean existsBySourceAndExternalId(String source, String externalId);

    Optional<Posting> findByFingerprint(String fingerprint);
}
