package com.hr.resumematch.llm;

import com.hr.resumematch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmRouter {

    private final AppProperties props;
    private final OpenAiLlmClient openAi;
    private final MockLlmClient mock;

    public LlmClient current() {
        String mode = props.getLlm().getMode() == null ? "auto" : props.getLlm().getMode().trim().toLowerCase();
        return switch (mode) {
            case "mock" -> mock;
            case "openai", "deepseek" -> openAi;
            default -> openAi.isConfigured() ? openAi : mock;
        };
    }

    public String resolvedProvider() {
        return current().providerName();
    }

    public boolean hasApiKey() {
        return openAi.isConfigured();
    }

    public String resolvedModel() {
        if (!"openai".equals(resolvedProvider())) {
            return "mock-rules";
        }
        return props.getLlm().getOpenai().getModel();
    }
}
