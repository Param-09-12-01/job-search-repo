package com.jobcopilot.service;

import com.jobcopilot.dto.profile.ProfileRequest;
import com.jobcopilot.dto.profile.ProfileResponse;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.exception.ResourceNotFoundException;
import com.jobcopilot.mapper.ProfileMapper;
import com.jobcopilot.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the single user's job-search profile. On first save it creates the profile;
 * subsequent saves update the existing one (idempotent single-profile semantics).
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        Profile profile = profileRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profile has not been created yet. Submit your profile first."));
        return profileMapper.toResponse(profile);
    }

    @Transactional(readOnly = true)
    public Profile getProfileEntityOrNull() {
        return profileRepository.findFirstByOrderByIdAsc().orElse(null);
    }

    @Transactional
    public ProfileResponse saveProfile(ProfileRequest request) {
        Profile profile = profileRepository.findFirstByOrderByIdAsc().orElse(null);
        if (profile == null) {
            profile = profileMapper.toEntity(request);
        } else {
            profileMapper.updateEntity(request, profile);
        }
        Profile saved = profileRepository.save(profile);
        return profileMapper.toResponse(saved);
    }
}
