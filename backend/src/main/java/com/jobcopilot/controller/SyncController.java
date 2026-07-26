package com.jobcopilot.controller;

import com.jobcopilot.dto.sync.GmailSyncResponse;
import com.jobcopilot.dto.sync.GmailSyncStatusResponse;
import com.jobcopilot.gmail.GmailService;
import com.jobcopilot.service.GmailSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Tag(name = "Sync", description = "Sync job applications from external sources (Gmail)")
public class SyncController {

    private final GmailSyncService gmailSyncService;
    private final GmailService gmailService;

    @PostMapping("/gmail")
    @Operation(summary = "Trigger Gmail sync — scans sent emails via Ollama and creates application entries")
    public ResponseEntity<GmailSyncResponse> triggerGmailSync(
            @RequestParam(required = false) Integer hours) {
        return ResponseEntity.ok(gmailSyncService.sync(hours));
    }

    @GetMapping("/gmail/status")
    @Operation(summary = "Get Gmail sync status (authorized, last sync, running)")
    public ResponseEntity<GmailSyncStatusResponse> getGmailSyncStatus() {
        return ResponseEntity.ok(gmailSyncService.getStatus());
    }

    @GetMapping("/gmail/auth-url")
    @Operation(summary = "Get Google OAuth URL for Gmail authorization")
    public ResponseEntity<Map<String, String>> getAuthUrl() {
        String url = gmailService.generateAuthUrl();
        if (url == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Gmail client ID not configured in settings"));
        }
        return ResponseEntity.ok(Map.of("authUrl", url));
    }

    @PostMapping("/gmail/auth-callback")
    @Operation(summary = "Exchange OAuth authorization code for refresh token (manual code paste)")
    public ResponseEntity<Map<String, String>> authCallback(@RequestParam(required = false) String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Authorization code is required"));
        }
        String result = gmailService.exchangeCode(code);
        return ResponseEntity.ok(Map.of("message", result));
    }

    @GetMapping(value = "/gmail/callback", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "OAuth callback endpoint — Google redirects here after authorization")
    public ResponseEntity<String> handleOAuthCallback(@RequestParam(required = false) String code,
                                                       @RequestParam(required = false) String error) {
        if (error != null) {
            return ResponseEntity.ok("<html><body><h2>Authorization failed</h2><p>Error: " + error + "</p></body></html>");
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("<html><body><h2>No authorization code received</h2></body></html>");
        }
        String result = gmailService.exchangeCode(code);
        boolean success = result.startsWith("Authorization successful");
        String html = "<html><body style='font-family:sans-serif;padding:40px;text-align:center'>"
                + "<h2>" + (success ? "✅ Authorization Successful!" : "❌ Authorization Failed") + "</h2>"
                + "<p>" + result + "</p>"
                + "<p>You can close this tab and return to the app.</p>"
                + "</body></html>";
        return ResponseEntity.ok(html);
    }

    @PostMapping("/gmail/disconnect")
    @Operation(summary = "Disconnect Gmail — clears the stored refresh token")
    public ResponseEntity<Map<String, String>> disconnectGmail() {
        gmailService.disconnect();
        return ResponseEntity.ok(Map.of("message", "Gmail disconnected. Refresh token cleared."));
    }

    @GetMapping("/gmail/profile")
    @Operation(summary = "Get the Gmail account profile (email, message counts)")
    public ResponseEntity<?> getGmailProfile() {
        String profileJson = gmailService.getProfileEmail();
        if (profileJson == null) {
            return ResponseEntity.ok(Map.of("error", "Not authenticated or failed to fetch profile"));
        }
        try {
            Object json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(profileJson);
            return ResponseEntity.ok(json);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("raw", profileJson));
        }
    }

    @GetMapping("/gmail/diagnostic")
    @Operation(summary = "Diagnostic: fetch messages without filters to check if Gmail API returns anything")
    public ResponseEntity<?> diagnostic() {
        String result = gmailService.diagnosticListMessages();
        try {
            Object json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
            return ResponseEntity.ok(json);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("raw", result));
        }
    }
}
