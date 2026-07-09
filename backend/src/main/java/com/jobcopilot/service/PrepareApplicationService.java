package com.jobcopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.automation.PrepareApplicationResponse;
import com.jobcopilot.entity.Posting;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.exception.BadRequestException;
import com.jobcopilot.exception.IntegrationException;
import com.jobcopilot.exception.ResourceNotFoundException;
import com.jobcopilot.repository.PostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Launches the Playwright helper that opens the user's browser, navigates to a posting's
 * application page, and fills known fields (name, email, phone, resume, links).
 *
 * <p><strong>Safety guarantees enforced here and in the helper script:</strong> the automation
 * NEVER clicks Apply/Submit, NEVER logs in, and NEVER bypasses authentication. It fills fields and
 * stops so the user reviews and submits manually.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepareApplicationService {

    private final PostingRepository postingRepository;
    private final ProfileService profileService;
    private final SettingsService settingsService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Prepare an application for the given posting. Blocks until the helper finishes launching the
     * browser (the browser itself stays open for the user).
     */
    public PrepareApplicationResponse prepare(Long postingId) {
        Posting posting = postingRepository.findById(postingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Posting", postingId));
        Profile profile = profileService.getProfileEntityOrNull();
        if (profile == null) {
            throw new BadRequestException("Create your profile before preparing applications");
        }

        AppProperties.Automation automation = properties.getAutomation();
        Path helperScript = Path.of(automation.getHelperScriptPath());
        if (!Files.exists(helperScript)) {
            throw new IntegrationException(
                    "Playwright helper script not found at: " + helperScript.toAbsolutePath());
        }

        Path payloadFile = writePayload(posting, profile, automation);
        try {
            runHelper(helperScript, payloadFile);
            return PrepareApplicationResponse.launched(posting.getUrl());
        } finally {
            deleteQuietly(payloadFile);
        }
    }

    private Path writePayload(Posting posting, Profile profile, AppProperties.Automation automation) {
        String resumePath = settingsService.getValue("profile.resume-path",
                profile.getResumePath() == null ? "" : profile.getResumePath());
        String browserPath = settingsService.getValue("automation.browser-path",
                automation.getBrowserExecutablePath());
        String browserProfilePath = settingsService.getValue("automation.browser-profile-path",
                automation.getBrowserProfilePath());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("url", posting.getUrl());
        payload.put("headless", automation.isHeadless());
        payload.put("browserExecutablePath", emptyToNull(browserPath));
        payload.put("browserProfilePath", emptyToNull(browserProfilePath));

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("fullName", profile.getFullName());
        fields.put("email", profile.getEmail());
        fields.put("phone", profile.getPhone());
        fields.put("resumePath", emptyToNull(resumePath));
        fields.put("linkedIn", profile.getLinkedIn());
        fields.put("github", profile.getGithub());
        fields.put("portfolio", profile.getPortfolio());
        payload.put("fields", fields);

        try {
            Path file = Files.createTempFile("prepare-application-", ".json");
            Files.writeString(file, objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            throw new IntegrationException("Failed to write automation payload: " + e.getMessage(), e);
        }
    }

    private void runHelper(Path helperScript, Path payloadFile) {
        List<String> command = new ArrayList<>();
        command.add("node");
        command.add(helperScript.toAbsolutePath().toString());
        command.add(payloadFile.toAbsolutePath().toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.directory(new File(System.getProperty("user.dir")));

        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IntegrationException("Playwright helper timed out");
            }
            int exit = process.exitValue();
            log.info("Playwright helper exited with code {}. Output:%n{}", exit, output);
            if (exit != 0) {
                throw new IntegrationException("Playwright helper failed (exit " + exit + "): " + output);
            }
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new IntegrationException("Failed to launch Playwright helper: " + e.getMessage(), e);
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.debug("Could not delete temp payload {}: {}", path, e.getMessage());
        }
    }
}
