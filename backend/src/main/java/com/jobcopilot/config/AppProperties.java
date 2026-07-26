package com.jobcopilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * Strongly-typed binding for the {@code app.*} configuration tree.
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NestedConfigurationProperty
    private Security security = new Security();
    @NestedConfigurationProperty
    private Scheduler scheduler = new Scheduler();
    @NestedConfigurationProperty
    private Scoring scoring = new Scoring();
    @NestedConfigurationProperty
    private Notification notification = new Notification();
    @NestedConfigurationProperty
    private Integration integration = new Integration();
    @NestedConfigurationProperty
    private Automation automation = new Automation();
    @NestedConfigurationProperty
    private Ai ai = new Ai();

    @Data
    public static class Security {
        private Jwt jwt = new Jwt();
        private BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
        private RateLimit rateLimit = new RateLimit();
        private Cors cors = new Cors();

        @Data
        public static class Jwt {
            private String secret;
            private long accessTokenTtlMinutes = 60;
            private long refreshTokenTtlDays = 7;
            private String issuer = "job-search-copilot";
        }

        @Data
        public static class BootstrapAdmin {
            private String username;
            private String password;
            private String email;
        }

        @Data
        public static class RateLimit {
            private int capacity = 100;
            private int refillTokens = 100;
            private int refillDurationSeconds = 60;
        }

        @Data
        public static class Cors {
            private List<String> allowedOrigins = List.of("http://localhost:5173");
        }
    }

    @Data
    public static class Scheduler {
        private boolean enabled = true;
        private String cron = "0 */30 * * * *";
        private String zone = "UTC";
    }

    @Data
    public static class Scoring {
        private Weights weights = new Weights();
        private int blacklistPenalty = 100;

        @Data
        public static class Weights {
            private int titleMatch = 25;
            private int locationMatch = 10;
            private int remoteMatch = 10;
            private int salaryMatch = 15;
            private int requiredKeywords = 20;
            private int experienceMatch = 10;
            private int freshness = 10;
        }
    }

    @Data
    public static class Notification {
        private int scoreThreshold = 70;
        private Email email = new Email();
        private Telegram telegram = new Telegram();

        @Data
        public static class Email {
            private boolean enabled = false;
            private boolean notifyOnMatch = false;
            private String from;
            private String to;
        }

        @Data
        public static class Telegram {
            private boolean enabled = false;
            private String botToken;
            private String chatId;
            private String apiBaseUrl = "https://api.telegram.org";
        }
    }

    @Data
    public static class Integration {
        private Adzuna adzuna = new Adzuna();
        private JSearch jsearch = new JSearch();
        private Greenhouse greenhouse = new Greenhouse();
        private Lever lever = new Lever();
        private RemoteOk remoteOk = new RemoteOk();
        private Findwork findwork = new Findwork();

        @Data
        public static class Adzuna {
            private boolean enabled = false;
            private String baseUrl = "https://api.adzuna.com/v1/api";
            private String appId;
            private String appKey;
            private String country = "us";
        }

        @Data
        public static class JSearch {
            private boolean enabled = false;
            private String baseUrl = "https://jsearch.p.rapidapi.com";
            private String apiKey;
            private String host = "jsearch.p.rapidapi.com";
        }

        @Data
        public static class Greenhouse {
            private boolean enabled = false;
            private String baseUrl = "https://boards-api.greenhouse.io/v1/boards";
            private String boards = "";
        }

        @Data
        public static class Lever {
            private boolean enabled = false;
            private String baseUrl = "https://api.lever.co/v0/postings";
            private String companies = "";
        }

        @Data
        public static class RemoteOk {
            private boolean enabled = false;
            private String baseUrl = "https://remoteok.com/api";
        }

        @Data
        public static class Findwork {
            private boolean enabled = false;
            private String baseUrl = "https://findwork.dev/api";
            private String apiKey;
        }
    }

    @Data
    public static class Automation {
        private String browserExecutablePath;
        private String browserProfilePath;
        private String helperScriptPath = "automation/prepare-application.mjs";
        private boolean headless = false;
    }

    @Data
    public static class Ai {
        private Ollama ollama = new Ollama();

        @Data
        public static class Ollama {
            private String baseUrl = "http://localhost:11434";
            private String model = "qwen2.5:12b";
            private int timeoutSeconds = 30;
        }
    }
}
