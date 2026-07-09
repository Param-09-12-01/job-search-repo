package com.jobcopilot.dto.automation;

/**
 * Result of a prepare-application automation run. {@code submitted} is ALWAYS false by design —
 * the system fills fields and stops; the user must click Apply manually.
 */
public record PrepareApplicationResponse(
        boolean launched,
        boolean submitted,
        String message,
        String postingUrl
) {
    public static PrepareApplicationResponse launched(String postingUrl) {
        return new PrepareApplicationResponse(true, false,
                "Browser launched and application fields were filled. Review and click Apply manually.",
                postingUrl);
    }
}
