package com.hr.resumematch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}

    @Data
    public static class HardRequirement {
        private String key;
        private String label;
        private String operator;
        private String value;
        /** 未通过时：fail 一票否决展示 / demote 降权 */
        private String onFail = "fail";
    }

    @Data
    public static class DimensionWeight {
        private String name;
        private String description;
        private double weight;
    }

    @Data
    public static class JobRequest {
        @NotBlank
        private String title;
        private String jdText;
        private List<HardRequirement> hardRequirements = new ArrayList<>();
        @NotEmpty
        private List<DimensionWeight> dimensions = new ArrayList<>();
    }

    @Data
    public static class JobResponse {
        private Long id;
        private String title;
        private String jdText;
        private List<HardRequirement> hardRequirements;
        private List<DimensionWeight> dimensions;
        private String createdAt;
    }

    @Data
    public static class CandidateResponse {
        private Long id;
        private Long jobId;
        private String fileName;
        private String parseStatus;
        private String parseError;
        private CandidateProfile profile;
        private List<RiskFlag> riskFlags;
        private String createdAt;
    }

    @Data
    public static class CandidateProfile {
        private String name;
        private String education;
        private Integer yearsOfExperience;
        private List<String> skills = new ArrayList<>();
        private List<WorkItem> experiences = new ArrayList<>();
        private List<String> highlights = new ArrayList<>();
        private String summary;
    }

    @Data
    public static class WorkItem {
        private String company;
        private String title;
        private String start;
        private String end;
        private String description;
    }

    @Data
    public static class RiskFlag {
        private String type;
        private String severity;
        private String detail;
    }

    @Data
    public static class DimensionScore {
        private String name;
        private double score;
        private double weight;
        private double weightedScore;
        private String evidence;
        private String gap;
        private double confidence;
    }

    @Data
    public static class GateCheck {
        private String key;
        private String label;
        private boolean passed;
        private String expected;
        private String actual;
        private String note;
    }

    @Data
    public static class MatchDetail {
        private List<GateCheck> gateChecks = new ArrayList<>();
        private List<DimensionScore> dimensions = new ArrayList<>();
    }

    @Data
    public static class MatchResponse {
        private Long id;
        private Long jobId;
        private Long candidateId;
        private String candidateName;
        private String fileName;
        private boolean passHardGate;
        private Double totalScore;
        private String summary;
        private String status;
        private MatchDetail detail;
        private InterviewPack interviewPack;
    }

    @Data
    public static class InterviewQuestion {
        private String category;
        private String question;
        private String intent;
        private String relatedRisk;
    }

    @Data
    public static class InterviewPack {
        private String opening;
        private List<InterviewQuestion> questions = new ArrayList<>();
        private List<String> closingTips = new ArrayList<>();
    }

    @Data
    public static class LlmStatusResponse {
        private String mode;
        private String provider;
        private boolean hasApiKey;
        private String model;
    }

    @Data
    public static class SeedResponse {
        private Long jobId;
        private int candidateCount;
        private String message;
    }
}
