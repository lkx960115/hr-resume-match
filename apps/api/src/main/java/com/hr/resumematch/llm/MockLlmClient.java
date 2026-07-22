package com.hr.resumematch.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hr.resumematch.dto.ApiDtos.*;
import com.hr.resumematch.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 无 API Key 时的可演示 Mock：基于关键词与简单规则生成可解释结果。
 */
@Component
public class MockLlmClient implements LlmClient {

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public CandidateProfile extractProfile(String resumeText, String jobTitle) {
        CandidateProfile p = new CandidateProfile();
        p.setName(firstMatch(resumeText, Pattern.compile("姓名[:：]\\s*(\\S+)"), "候选人"));
        p.setEducation(detectEducation(resumeText));
        p.setYearsOfExperience(detectYears(resumeText));
        p.setSkills(detectSkills(resumeText));
        p.setHighlights(p.getSkills().stream().limit(3).map(s -> "具备 " + s + " 相关经验").collect(Collectors.toList()));
        p.setSummary("（Mock）基于规则从简历抽取的摘要，岗位：" + jobTitle);
        WorkItem w = new WorkItem();
        w.setCompany(firstMatch(resumeText, Pattern.compile("([^\\n]{2,20}公司)"), "某科技公司"));
        w.setTitle(firstMatch(resumeText, Pattern.compile("职位[:：]\\s*(\\S+)"), "工程师"));
        w.setStart("2020-01");
        w.setEnd("至今");
        w.setDescription(truncate(resumeText, 200));
        p.setExperiences(List.of(w));
        return p;
    }

    @Override
    public MatchDetail scoreMatch(String jobJson, String profileJson, String resumeText) {
        Map<String, Object> job = JsonUtils.fromJson(jobJson, new TypeReference<>() {});
        CandidateProfile profile = JsonUtils.fromJson(profileJson, CandidateProfile.class);
        MatchDetail detail = new MatchDetail();

        List<Map<String, Object>> hard = (List<Map<String, Object>>) job.getOrDefault("hardRequirements", List.of());
        List<GateCheck> gates = new ArrayList<>();
        String text = (resumeText == null ? "" : resumeText) + " " + JsonUtils.toJson(profile);
        for (Map<String, Object> h : hard) {
            GateCheck g = new GateCheck();
            g.setKey(str(h.get("key")));
            g.setLabel(str(h.get("label")));
            g.setExpected(str(h.get("value")));
            String key = g.getKey();
            boolean passed;
            String actual;
            if ("education".equals(key)) {
                actual = profile.getEducation() == null ? "未知" : profile.getEducation();
                passed = educationRank(actual) >= educationRank(g.getExpected());
            } else if ("years".equals(key)) {
                actual = String.valueOf(profile.getYearsOfExperience());
                passed = profile.getYearsOfExperience() != null
                        && profile.getYearsOfExperience() >= parseIntSafe(g.getExpected());
            } else if ("skill".equals(key)) {
                actual = String.join(",", profile.getSkills());
                passed = text.toLowerCase().contains(g.getExpected().toLowerCase());
            } else {
                actual = "见简历";
                passed = text.toLowerCase().contains(g.getExpected().toLowerCase());
            }
            g.setActual(actual);
            g.setPassed(passed);
            g.setNote(passed ? "满足门槛" : "未满足，需面试核验");
            gates.add(g);
        }
        detail.setGateChecks(gates);

        List<Map<String, Object>> dims = (List<Map<String, Object>>) job.getOrDefault("dimensions", List.of());
        List<DimensionScore> scores = new ArrayList<>();
        for (Map<String, Object> d : dims) {
            String name = str(d.get("name"));
            double weight = toDouble(d.get("weight"));
            DimensionScore ds = new DimensionScore();
            ds.setName(name);
            ds.setWeight(weight);
            int hit = 0;
            List<String> evidenceBits = new ArrayList<>();
            for (String skill : Optional.ofNullable(profile.getSkills()).orElse(List.of())) {
                if (name.contains(skill) || skill.contains(name) || text.contains(skill)) {
                    hit++;
                    evidenceBits.add(skill);
                }
            }
            // 关键词命中
            String[] tokens = name.split("[/、\\s]+");
            for (String t : tokens) {
                if (t.length() >= 2 && text.toLowerCase().contains(t.toLowerCase())) {
                    hit++;
                    evidenceBits.add(t);
                }
            }
            double score = Math.min(95, 45 + hit * 12 + (profile.getYearsOfExperience() == null ? 0 : Math.min(20, profile.getYearsOfExperience() * 2)));
            ds.setScore(score);
            ds.setWeightedScore(Math.round(score * weight * 100.0) / 100.0);
            ds.setEvidence(evidenceBits.isEmpty() ? "（Mock）简历中相关表述有限，建议面试深挖" : "命中：" + String.join("、", evidenceBits.stream().distinct().limit(5).toList()));
            ds.setGap(score >= 70 ? "暂无明显缺口" : "证据不足，需追问项目细节");
            ds.setConfidence(0.55 + Math.min(0.35, hit * 0.05));
            scores.add(ds);
        }
        detail.setDimensions(scores);
        return detail;
    }

    @Override
    public InterviewPack buildInterviewPack(String jobTitle, String profileJson, String matchSummary, String risksJson) {
        InterviewPack pack = new InterviewPack();
        pack.setOpening("针对「" + jobTitle + "」的结构化面试开场：先请候选人用 2 分钟介绍与岗位最相关的一段经历。");
        List<InterviewQuestion> qs = new ArrayList<>();
        qs.add(q("硬性核验", "请说明你最近一段工作的到岗时间、汇报对象与核心职责边界。", "核验履历真实性", ""));
        qs.add(q("能力深挖", "挑一个你主导的后端项目，讲清楚流量、存储选型、你负责的模块与量化结果。", "验证工程深度", ""));
        qs.add(q("能力深挖", "当线上出现延迟升高时，你的排查路径是什么？举一个真实案例。", "验证问题定位能力", ""));
        qs.add(q("风险追问", "简历中提到的技术栈，哪些是你日常主力，哪些只是了解？请按熟练度排序。", "防止技能夸大", "技能夸大"));
        qs.add(q("风险追问", "若存在空窗或跳槽较密，请说明原因与这段时间的学习/项目产出。", "澄清履历连续性", "履历风险"));
        qs.add(q("情景题", "给你两周完成一个订单查询接口的性能优化，你会如何拆解目标与验收标准？", "看目标拆解与落地", ""));
        pack.setQuestions(qs);
        pack.setClosingTips(List.of(
                "对照匹配报告中的缺口逐条追问，避免只聊亮点",
                "要求候选人给出可验证的数据（QPS、耗时、故障次数）",
                "（Mock 模式）当前问题为规则生成，配置 OpenAI Key 后可个性化出题"
        ));
        return pack;
    }

    private static InterviewQuestion q(String cat, String question, String intent, String risk) {
        InterviewQuestion iq = new InterviewQuestion();
        iq.setCategory(cat);
        iq.setQuestion(question);
        iq.setIntent(intent);
        iq.setRelatedRisk(risk);
        return iq;
    }

    private static List<String> detectSkills(String text) {
        String[] dict = {"Java", "Spring", "Spring Boot", "MySQL", "Redis", "Kafka", "Docker", "Kubernetes",
                "Python", "React", "TypeScript", "微服务", "MyBatis", "JVM", "Nginx"};
        List<String> hit = new ArrayList<>();
        String lower = text == null ? "" : text;
        for (String s : dict) {
            if (lower.toLowerCase().contains(s.toLowerCase())) {
                hit.add(s);
            }
        }
        return hit.isEmpty() ? List.of("Java") : hit;
    }

    private static String detectEducation(String text) {
        if (text == null) return "未知";
        if (text.contains("博士")) return "博士";
        if (text.contains("硕士") || text.contains("研究生")) return "硕士";
        if (text.contains("本科") || text.contains("学士")) return "本科";
        if (text.contains("大专") || text.contains("专科")) return "大专";
        return "未知";
    }

    private static int detectYears(String text) {
        if (text == null) return 1;
        Matcher m = Pattern.compile("(\\d+)\\s*年").matcher(text);
        int max = 0;
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max > 0 ? Math.min(max, 20) : 3;
    }

    private static int educationRank(String edu) {
        if (edu == null) return 0;
        return switch (edu) {
            case "博士" -> 4;
            case "硕士" -> 3;
            case "本科" -> 2;
            case "大专" -> 1;
            default -> 0;
        };
    }

    private static String firstMatch(String text, Pattern p, String def) {
        if (text == null) return def;
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : def;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}
