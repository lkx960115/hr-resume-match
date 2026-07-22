package com.hr.resumematch.llm;

import com.hr.resumematch.config.AppProperties;
import com.hr.resumematch.dto.ApiDtos.*;
import com.hr.resumematch.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiLlmClient implements LlmClient {

    private final AppProperties props;
    private final RestClient.Builder restClientBuilder;

    @Override
    public String providerName() {
        return "openai";
    }

    public boolean isConfigured() {
        String key = props.getLlm().getOpenai().getApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    public CandidateProfile extractProfile(String resumeText, String jobTitle) {
        String prompt = """
                你是招聘简历结构化助手。请从简历原文提取结构化信息，只输出 JSON，不要 markdown。
                目标岗位：%s
                JSON Schema:
                {
                  "name": "string",
                  "education": "string",
                  "yearsOfExperience": number,
                  "skills": ["string"],
                  "experiences": [{"company":"","title":"","start":"","end":"","description":""}],
                  "highlights": ["string"],
                  "summary": "string"
                }
                简历原文：
                %s
                """.formatted(jobTitle, truncate(resumeText, 12000));
        return JsonUtils.fromJson(chat(prompt), CandidateProfile.class);
    }

    @Override
    public MatchDetail scoreMatch(String jobJson, String profileJson, String resumeText) {
        String prompt = """
                你是招聘匹配评估助手。根据岗位标准与候选人画像打分，只输出 JSON。
                规则：
                1) gateChecks：逐条评估 hardRequirements，passed true/false，给出 expected/actual/note
                2) dimensions：对每个维度给 score(0-100)、evidence(引用简历原文)、gap、confidence(0-1)
                3) weight 字段请原样带回岗位权重
                岗位 JSON：
                %s
                候选人画像 JSON：
                %s
                简历摘录：
                %s
                输出 Schema:
                {
                  "gateChecks":[{"key":"","label":"","passed":true,"expected":"","actual":"","note":""}],
                  "dimensions":[{"name":"","score":0,"weight":0,"evidence":"","gap":"","confidence":0}]
                }
                """.formatted(jobJson, profileJson, truncate(resumeText, 8000));
        MatchDetail detail = JsonUtils.fromJson(chat(prompt), MatchDetail.class);
        if (detail != null && detail.getDimensions() != null) {
            for (DimensionScore d : detail.getDimensions()) {
                d.setWeightedScore(round(d.getScore() * d.getWeight()));
            }
        }
        return detail;
    }

    @Override
    public InterviewPack buildInterviewPack(String jobTitle, String profileJson, String matchSummary, String risksJson) {
        String prompt = """
                你是结构化面试出题助手。针对岗位「%s」生成面试包，只输出 JSON。
                候选人画像：%s
                匹配摘要：%s
                风险点：%s
                Schema:
                {
                  "opening":"string",
                  "questions":[{"category":"硬性核验|能力深挖|风险追问|情景题","question":"","intent":"","relatedRisk":""}],
                  "closingTips":["string"]
                }
                要求：至少 6 个问题，覆盖风险点。
                """.formatted(jobTitle, profileJson, matchSummary, risksJson);
        return JsonUtils.fromJson(chat(prompt), InterviewPack.class);
    }

    private String chat(String userPrompt) {
        var oa = props.getLlm().getOpenai();
        long start = System.currentTimeMillis();
        int promptChars = userPrompt == null ? 0 : userPrompt.length();
        log.info("LLM 请求开始: model={}, baseUrl={}, promptChars={}",
                oa.getModel(), oa.getBaseUrl(), promptChars);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", oa.getModel());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", "你是严谨的 HR AI，只输出合法 JSON。"),
                Map.of("role", "user", "content", userPrompt)
        ));

        try {
            RestClient client = restClientBuilder
                    .baseUrl(oa.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + oa.getApiKey())
                    .build();

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) {
                throw new IllegalStateException("OpenAI 兼容接口返回空响应");
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("OpenAI 兼容接口无 choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = String.valueOf(message.get("content"));
            String json = extractJson(content);
            log.info("LLM 请求成功: model={}, promptChars={}, responseChars={}, costMs={}",
                    oa.getModel(), promptChars, json.length(), System.currentTimeMillis() - start);
            return json;
        } catch (Exception e) {
            log.error("LLM 请求失败: model={}, baseUrl={}, promptChars={}, costMs={}, error={}",
                    oa.getModel(), oa.getBaseUrl(), promptChars, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    static String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            Pattern p = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(trimmed);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
