package com.jobcopilot.scheduler;

import com.jobcopilot.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron-driven trigger for the ingestion pipeline. The cron expression and enabled flag come from
 * configuration ({@code app.scheduler.*}); runtime overrides via the admin panel are honored by
 * {@link #isEnabledAtRuntime()} guarding each fire.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobFetchScheduler {

    private final JobFetchRunner runner;
    private final AppProperties properties;
    private final com.jobcopilot.service.SettingsService settingsService;

    @Scheduled(cron = "${app.scheduler.cron}", zone = "${app.scheduler.zone:UTC}")
    public void scheduledRun() {
        if (!isEnabledAtRuntime()) {
            log.debug("Scheduler disabled — skipping run");
            return;
        }
        log.info("Scheduled ingestion run starting");
        runner.runOnce();
    }

    private boolean isEnabledAtRuntime() {
        return settingsService.getBooleanValue("scheduler.enabled",
                properties.getScheduler().isEnabled());
    }
}
