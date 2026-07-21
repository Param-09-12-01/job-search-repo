package com.jobcopilot.config;

import com.jobcopilot.service.GhostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs one-time tasks on application startup in a separate thread so they
 * don't block the main boot sequence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupRunner implements ApplicationRunner {

    private final GhostingService ghostingService;

    @Override
    public void run(ApplicationArguments args) {
        Thread ghostThread = new Thread(() -> {
            try {
                log.info("Ghosting check started");
                int count = ghostingService.ghostStaleApplied();
                log.info("Ghosting check completed: {} application(s) ghosted", count);
            } catch (Exception e) {
                log.error("Ghosting check failed", e);
            }
        }, "ghosting-check");
        ghostThread.setDaemon(true);
        ghostThread.start();
    }
}
