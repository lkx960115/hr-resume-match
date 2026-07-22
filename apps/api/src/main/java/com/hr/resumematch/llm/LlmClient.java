package com.hr.resumematch.llm;

import com.hr.resumematch.dto.ApiDtos.*;

public interface LlmClient {
    String providerName();

    CandidateProfile extractProfile(String resumeText, String jobTitle);

    MatchDetail scoreMatch(String jobJson, String profileJson, String resumeText);

    InterviewPack buildInterviewPack(String jobTitle, String profileJson, String matchSummary, String risksJson);
}
