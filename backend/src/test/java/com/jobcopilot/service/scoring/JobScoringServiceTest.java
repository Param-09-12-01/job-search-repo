package com.jobcopilot.service.scoring;

import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.entity.enums.RemotePreference;
import com.jobcopilot.entity.enums.Seniority;
import com.jobcopilot.util.JsonListUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the weighted {@link JobScoringService}. Exercises each criterion and the
 * blacklist short-circuit using the default weight configuration.
 */
class JobScoringServiceTest {

    private JobScoringService scoringService;
    private Profile profile;

    @BeforeEach
    void setUp() {
        // Default weights from AppProperties (title 25, location 10, remote 10, salary 15,
        // keywords 20, experience 10, freshness 10 -> total 100).
        scoringService = new JobScoringService(new AppProperties());

        profile = new Profile();
        profile.setTitles(JsonListUtil.toJson(List.of("Backend Engineer", "Java Developer")));
        profile.setLocations(JsonListUtil.toJson(List.of("Berlin", "Remote")));
        profile.setKeywords(JsonListUtil.toJson(List.of("spring boot", "java", "aws")));
        profile.setExcludedCompanies(JsonListUtil.toJson(List.of("EvilCorp")));
        profile.setSalaryMinimum(80_000);
        profile.setRemotePreference(RemotePreference.REMOTE);
        profile.setSeniority(Seniority.SENIOR);
    }

    private NormalizedJob job(String title, String company, String location, boolean remote,
                              Integer salaryMax, String description, LocalDateTime postedAt) {
        return new NormalizedJob("ADZUNA", "ext-1", title, company, location, remote,
                null, null, salaryMax, description, "https://example.com/job", postedAt);
    }

    @Test
    @DisplayName("A perfectly matching senior remote job scores very high")
    void perfectMatchScoresHigh() {
        NormalizedJob perfect = job(
                "Senior Backend Engineer", "GoodCorp", "Remote", true,
                120_000, "We use Spring Boot, Java and AWS heavily.", LocalDateTime.now());

        int score = scoringService.score(perfect, profile);

        assertThat(score).isGreaterThanOrEqualTo(90);
    }

    @Test
    @DisplayName("Blacklisted company collapses score to zero")
    void blacklistedCompanyScoresZero() {
        NormalizedJob blacklisted = job(
                "Senior Backend Engineer", "EvilCorp", "Remote", true,
                120_000, "Spring Boot Java AWS", LocalDateTime.now());

        assertThat(scoringService.score(blacklisted, profile)).isZero();
    }

    @Test
    @DisplayName("Irrelevant title with no keyword hits scores low")
    void irrelevantJobScoresLow() {
        NormalizedJob irrelevant = job(
                "Registered Nurse", "Hospital", "Onsite Nowhere", false,
                20_000, "Patient care", LocalDateTime.now().minusDays(20));

        assertThat(scoringService.score(irrelevant, profile)).isLessThan(40);
    }

    @Test
    @DisplayName("Salary below minimum reduces the salary contribution")
    void lowSalaryReducesScore() {
        NormalizedJob highSalary = job("Senior Backend Engineer", "GoodCorp", "Remote", true,
                120_000, "Spring Boot Java AWS", LocalDateTime.now());
        NormalizedJob lowSalary = job("Senior Backend Engineer", "GoodCorp", "Remote", true,
                40_000, "Spring Boot Java AWS", LocalDateTime.now());

        assertThat(scoringService.score(lowSalary, profile))
                .isLessThan(scoringService.score(highSalary, profile));
    }

    @Test
    @DisplayName("Fresher postings outrank stale ones, all else equal")
    void fresherJobsScoreHigher() {
        NormalizedJob fresh = job("Senior Backend Engineer", "GoodCorp", "Remote", true,
                120_000, "Spring Boot Java AWS", LocalDateTime.now());
        NormalizedJob stale = job("Senior Backend Engineer", "GoodCorp", "Remote", true,
                120_000, "Spring Boot Java AWS", LocalDateTime.now().minusDays(29));

        assertThat(scoringService.score(fresh, profile))
                .isGreaterThan(scoringService.score(stale, profile));
    }

    @Test
    @DisplayName("Remote preference penalizes onsite-only jobs")
    void remotePreferencePenalizesOnsite() {
        NormalizedJob onsite = job("Senior Backend Engineer", "GoodCorp", "Berlin", false,
                120_000, "Spring Boot Java AWS", LocalDateTime.now());
        NormalizedJob remote = job("Senior Backend Engineer", "GoodCorp", "Remote", true,
                120_000, "Spring Boot Java AWS", LocalDateTime.now());

        assertThat(scoringService.score(onsite, profile))
                .isLessThan(scoringService.score(remote, profile));
    }

    @Test
    @DisplayName("Score is always clamped to the 0-100 range")
    void scoreAlwaysInRange() {
        NormalizedJob any = job("Anything", "AnyCo", "Anywhere", false,
                null, null, null);
        int score = scoringService.score(any, profile);
        assertThat(score).isBetween(0, 100);
    }

    @Test
    @DisplayName("Null profile yields a freshness-only, in-range score")
    void nullProfileScores() {
        NormalizedJob fresh = job("Senior Backend Engineer", "GoodCorp", "Remote", true,
                120_000, "Spring Boot", LocalDateTime.now());
        assertThat(scoringService.score(fresh, null)).isBetween(0, 100);
    }
}
