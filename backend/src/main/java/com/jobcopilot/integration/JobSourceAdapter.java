package com.jobcopilot.integration;

import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.enums.JobSourceType;

import java.util.List;

/**
 * Contract every external job provider must implement. New providers are added by implementing
 * this interface and registering the bean — the ingestion pipeline discovers all adapters
 * automatically, so no other code needs to change.
 *
 * <p>The pipeline calls the adapter as: {@link #validate()} → {@link #fetchJobs()} →
 * {@link #normalize(List)}. Raw fetch output is provider-specific ({@code Object}); each adapter
 * knows how to normalize its own payloads into {@link NormalizedJob} value objects.</p>
 */
public interface JobSourceAdapter {

    /** The provider type this adapter handles. */
    JobSourceType type();

    /**
     * Whether the adapter is enabled and has the configuration/credentials it needs to run.
     * The pipeline skips adapters that report {@code false}.
     */
    boolean validate();

    /**
     * Fetch raw postings from the provider. Implementations should be resilient and throw
     * {@code IntegrationException} on unrecoverable failures so the pipeline can isolate them.
     *
     * @return provider-native raw records (adapter-specific type)
     */
    List<Object> fetchJobs();

    /**
     * Convert raw provider records into normalized job value objects. Records that cannot be
     * normalized (missing required fields) are skipped.
     */
    List<NormalizedJob> normalize(List<Object> rawJobs);
}
