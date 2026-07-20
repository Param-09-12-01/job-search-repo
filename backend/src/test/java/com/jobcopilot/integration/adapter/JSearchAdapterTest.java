package com.jobcopilot.integration.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.enums.JobSourceType;
import com.jobcopilot.service.ProfileService;
import com.jobcopilot.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link JSearchAdapter#normalize} — verifies raw provider payloads are mapped to
 * {@link NormalizedJob} and that records missing required fields are skipped.
 */
class JSearchAdapterTest {

    private JSearchAdapter adapter;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient restClient = Mockito.mock(RestClient.class);
        ProfileService profileService = Mockito.mock(ProfileService.class);
        SettingsService settingsService = Mockito.mock(SettingsService.class);
        adapter = new JSearchAdapter(restClient, new AppProperties(), profileService, settingsService);
    }

    @Test
    void normalizesValidRecord() throws Exception {
        String json = """
                {
                  "job_id": "abc123",
                  "job_title": "Senior Java Developer",
                  "job_apply_link": "https://apply.example.com/abc123",
                  "employer_name": "GoodCorp",
                  "job_city": "Berlin",
                  "job_country": "DE",
                  "job_is_remote": true,
                  "job_min_salary": 90000,
                  "job_max_salary": 120000,
                  "job_description": "Spring Boot and Java role",
                  "job_posted_at_datetime_utc": "2026-01-01T10:00:00.000Z"
                }
                """;
        JsonNode node = mapper.readTree(json);

        List<NormalizedJob> result = adapter.normalize(List.of(node));

        assertThat(result).hasSize(1);
        NormalizedJob job = result.get(0);
        assertThat(job.source()).isEqualTo(JobSourceType.JSEARCH.name());
        assertThat(job.externalId()).isEqualTo("abc123");
        assertThat(job.title()).isEqualTo("Senior Java Developer");
        assertThat(job.company()).isEqualTo("GoodCorp");
        assertThat(job.location()).isEqualTo("Berlin, DE");
        assertThat(job.remote()).isTrue();
        assertThat(job.salaryMax()).isEqualTo(120000);
        assertThat(job.url()).isEqualTo("https://apply.example.com/abc123");
    }

    @Test
    void skipsRecordMissingRequiredFields() throws Exception {
        JsonNode node = mapper.readTree("{\"job_title\": \"No id or link\"}");

        assertThat(adapter.normalize(List.of(node))).isEmpty();
    }

    @Test
    void emptyInputYieldsEmptyOutput() {
        assertThat(adapter.normalize(List.of())).isEmpty();
    }
}
