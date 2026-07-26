package com.jobcopilot.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private Ollama ollama = new Ollama();

    @Data
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:12b";
        private int timeoutSeconds = 30;
    }
}
