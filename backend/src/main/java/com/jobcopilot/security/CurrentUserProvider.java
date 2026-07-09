package com.jobcopilot.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Convenience accessor for the currently authenticated username, used by audit logging.
 */
@Component
public class CurrentUserProvider {

    public String getUsernameOrSystem() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUserDetails details) {
            return details.getUsername();
        }
        return "system";
    }
}
