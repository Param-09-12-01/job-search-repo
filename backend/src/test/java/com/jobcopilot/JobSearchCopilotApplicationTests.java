package com.jobcopilot;

import com.jobcopilot.service.scoring.JobScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration smoke test: verifies the full Spring application context boots with the test profile
 * (H2, no Flyway, no Redis) and core beans are wired.
 */
@SpringBootTest
@ActiveProfiles("test")
class JobSearchCopilotApplicationTests {

    @Autowired(required = false)
    private JobScoringService jobScoringService;

    @Test
    void contextLoads() {
        assertThat(jobScoringService).isNotNull();
    }
}
