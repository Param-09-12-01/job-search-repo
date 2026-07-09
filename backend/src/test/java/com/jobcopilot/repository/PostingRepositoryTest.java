package com.jobcopilot.repository;

import com.jobcopilot.entity.Posting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.jobcopilot.config.PersistenceConfig;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice test for {@link PostingRepository}, backed by in-memory H2.
 * Verifies de-duplication predicates and specification-friendly persistence.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(PersistenceConfig.class)
class PostingRepositoryTest {

    @Autowired
    private PostingRepository postingRepository;

    private Posting newPosting(String source, String externalId, String fingerprint) {
        return Posting.builder()
                .source(source)
                .externalId(externalId)
                .title("Backend Engineer")
                .company("GoodCorp")
                .location("Remote")
                .remote(true)
                .url("https://example.com/job")
                .score(75)
                .fingerprint(fingerprint)
                .postedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void persistsAndDetectsDuplicateByFingerprint() {
        postingRepository.save(newPosting("ADZUNA", "a-1", "fingerprint-1"));

        assertThat(postingRepository.existsByFingerprint("fingerprint-1")).isTrue();
        assertThat(postingRepository.existsByFingerprint("does-not-exist")).isFalse();
    }

    @Test
    void detectsDuplicateBySourceAndExternalId() {
        postingRepository.save(newPosting("LEVER", "netflix:42", "fp-lever"));

        assertThat(postingRepository.existsBySourceAndExternalId("LEVER", "netflix:42")).isTrue();
        assertThat(postingRepository.existsBySourceAndExternalId("LEVER", "other")).isFalse();
    }

    @Test
    void savesMultipleDistinctPostings() {
        postingRepository.save(newPosting("ADZUNA", "a-1", "fp-a"));
        postingRepository.save(newPosting("JSEARCH", "j-1", "fp-b"));

        assertThat(postingRepository.count()).isEqualTo(2);
    }
}
