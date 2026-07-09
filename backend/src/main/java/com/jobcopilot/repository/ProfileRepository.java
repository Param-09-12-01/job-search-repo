package com.jobcopilot.repository;

import com.jobcopilot.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    /** The platform is single-user; the first (and only) profile is the active one. */
    Optional<Profile> findFirstByOrderByIdAsc();
}
