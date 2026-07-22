package com.hr.resumematch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String uploadDir = "./uploads";
    private Llm llm = new Llm();

    @Data
    public static class Llm {
        /** auto | openai | mock */
        private String mode = "auto";
        private OpenAi openai = new OpenAi();
    }

    @Data
    public static class OpenAi {
        private String apiKey = "";
        private String baseUrl = "https://hub.shhdpz.cn";
        private String model = "gpt-5.5";
        private int timeoutSeconds = 90;
    }
}
