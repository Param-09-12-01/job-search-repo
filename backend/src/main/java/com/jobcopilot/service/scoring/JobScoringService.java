package com.jobcopilot.service.scoring;

import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.entity.enums.RemotePreference;
import com.jobcopilot.entity.enums.Seniority;
import com.jobcopilot.util.JsonListUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Weighted job-scoring engine producing a 0-100 relevance score for a posting given a profile.
 *
 * <p>Each criterion contributes a fraction (0.0-1.0) of its configured weight. Blacklisted
 * companies collapse the score to zero regardless of other criteria. Weights are configurable
 * via {@code app.scoring.weights.*}.</p>
 */
@Service
@RequiredArgsConstructor
public class JobScoringService {

    private final AppProperties properties;

    /**
     * Score a normalized job against the profile. Returns 0-100.
     * A null profile yields a neutral freshness-only score so unconfigured installs still rank recency.
     */
    public int score(NormalizedJob job, Profile profile) {
        AppProperties.Scoring.Weights w = properties.getScoring().getWeights();

        if (profile == null) {
            return clamp(Math.round(freshnessFactor(job.postedAt()) * totalWeight(w)));
        }

        // Company blacklist short-circuits to zero.
        if (isBlacklisted(job.company(), profile)) {
            return 0;
        }

        double earned = 0;
        earned += titleFactor(job.title(), profile) * w.getTitleMatch();
        earned += locationFactor(job, profile) * w.getLocationMatch();
        earned += remoteFactor(job, profile) * w.getRemoteMatch();
        earned += salaryFactor(job, profile) * w.getSalaryMatch();
        earned += keywordsFactor(job, profile) * w.getRequiredKeywords();
        earned += experienceFactor(job.title(), profile) * w.getExperienceMatch();
        earned += freshnessFactor(job.postedAt()) * w.getFreshness();

        double normalized = earned / totalWeight(w) * 100.0;
        return clamp(Math.round(normalized));
    }

    private int totalWeight(AppProperties.Scoring.Weights w) {
        return w.getTitleMatch() + w.getLocationMatch() + w.getRemoteMatch() + w.getSalaryMatch()
                + w.getRequiredKeywords() + w.getExperienceMatch() + w.getFreshness();
    }

    // --- Criterion factors (each returns 0.0 - 1.0) -------------------------------------------

    private double titleFactor(String title, Profile profile) {
        List<String> desired = JsonListUtil.fromJson(profile.getTitles());
        if (desired.isEmpty()) {
            return 0.5; // no preference expressed -> neutral
        }
        String lower = lower(title);
        for (String t : desired) {
            String candidate = lower(t);
            if (candidate.isBlank()) {
                continue;
            }
            if (lower.equals(candidate)) {
                return 1.0;
            }
            if (lower.contains(candidate) || candidate.contains(lower)) {
                return 0.85;
            }
            if (tokenOverlap(lower, candidate) >= 0.5) {
                return 0.7;
            }
        }
        return 0.0;
    }

    private double locationFactor(NormalizedJob job, Profile profile) {
        if (job.remote()) {
            return 1.0;
        }
        List<String> desired = JsonListUtil.fromJson(profile.getLocations());
        if (desired.isEmpty()) {
            return 0.5;
        }
        String loc = lower(job.location());
        if (loc.isBlank()) {
            return 0.3;
        }
        for (String d : desired) {
            String candidate = lower(d);
            if (!candidate.isBlank() && (loc.contains(candidate) || candidate.contains(loc))) {
                return 1.0;
            }
        }
        return 0.0;
    }

    private double remoteFactor(NormalizedJob job, Profile profile) {
        RemotePreference pref = profile.getRemotePreference() == null
                ? RemotePreference.ANY : profile.getRemotePreference();
        return switch (pref) {
            case ANY -> 1.0;
            case REMOTE -> job.remote() ? 1.0 : 0.0;
            case ONSITE -> job.remote() ? 0.2 : 1.0;
            case HYBRID -> job.remote() ? 0.8 : 0.6;
        };
    }

    private double salaryFactor(NormalizedJob job, Profile profile) {
        Integer minRequired = profile.getSalaryMinimum();
        if (minRequired == null || minRequired <= 0) {
            return 0.5; // no salary requirement -> neutral
        }
        Integer offered = job.salaryMax() != null ? job.salaryMax() : job.salaryMin();
        if (offered == null || offered <= 0) {
            return 0.4; // unknown salary -> slightly below neutral
        }
        if (offered >= minRequired) {
            return 1.0;
        }
        // Partial credit proportional to how close it is.
        double ratio = (double) offered / minRequired;
        return Math.max(0.0, Math.min(1.0, ratio));
    }

    private double keywordsFactor(NormalizedJob job, Profile profile) {
        List<String> keywords = JsonListUtil.fromJson(profile.getKeywords());
        if (keywords.isEmpty()) {
            return 0.5;
        }
        String haystack = lower(job.title() + " " + safe(job.description()));
        long matches = keywords.stream()
                .map(this::lower)
                .filter(k -> !k.isBlank() && haystack.contains(k))
                .count();
        return (double) matches / keywords.size();
    }

    private double experienceFactor(String title, Profile profile) {
        Seniority pref = profile.getSeniority();
        if (pref == null) {
            return 0.5;
        }
        String lower = lower(title);
        Seniority detected = detectSeniority(lower);
        if (detected == null) {
            return 0.5; // unspecified in posting
        }
        int distance = Math.abs(detected.ordinal() - pref.ordinal());
        return switch (distance) {
            case 0 -> 1.0;
            case 1 -> 0.6;
            case 2 -> 0.3;
            default -> 0.0;
        };
    }

    private double freshnessFactor(LocalDateTime postedAt) {
        if (postedAt == null) {
            return 0.5;
        }
        long days = Duration.between(postedAt, LocalDateTime.now()).toDays();
        if (days <= 0) {
            return 1.0;
        }
        if (days >= 30) {
            return 0.0;
        }
        return 1.0 - (days / 30.0);
    }

    // --- Helpers ------------------------------------------------------------------------------

    private boolean isBlacklisted(String company, Profile profile) {
        if (company == null || company.isBlank()) {
            return false;
        }
        String lower = lower(company);
        return JsonListUtil.fromJson(profile.getExcludedCompanies()).stream()
                .map(this::lower)
                .anyMatch(excluded -> !excluded.isBlank()
                        && (lower.contains(excluded) || excluded.contains(lower)));
    }

    private Seniority detectSeniority(String title) {
        if (title.contains("principal") || title.contains("staff")) {
            return Seniority.PRINCIPAL;
        }
        if (title.contains("lead") || title.contains("architect")) {
            return Seniority.LEAD;
        }
        if (title.contains("senior") || title.contains("sr.") || title.contains("sr ")) {
            return Seniority.SENIOR;
        }
        if (title.contains("junior") || title.contains("jr.") || title.contains("jr ")) {
            return Seniority.JUNIOR;
        }
        if (title.contains("intern")) {
            return Seniority.INTERN;
        }
        if (title.contains("mid") || title.contains("intermediate")) {
            return Seniority.MID;
        }
        return null;
    }

    private double tokenOverlap(String a, String b) {
        String[] at = a.split("\\s+");
        String[] bt = b.split("\\s+");
        if (at.length == 0) {
            return 0.0;
        }
        long common = java.util.Arrays.stream(at)
                .filter(token -> token.length() > 2)
                .filter(token -> java.util.Arrays.asList(bt).contains(token))
                .count();
        return (double) common / at.length;
    }

    private String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private int clamp(long value) {
        return (int) Math.max(0, Math.min(100, value));
    }
}
