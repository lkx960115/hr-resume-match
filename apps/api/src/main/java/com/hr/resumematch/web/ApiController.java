package com.hr.resumematch.web;

import com.hr.resumematch.dto.ApiDtos.*;
import com.hr.resumematch.service.DemoSeedService;
import com.hr.resumematch.service.JobMatchService;
import com.hr.resumematch.util.PrivacyUtil;
import com.hr.resumematch.config.AppProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final JobMatchService jobMatchService;
    private final DemoSeedService demoSeedService;
    private final AppProperties appProperties;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/llm/status")
    public LlmStatusResponse llmStatus() {
        LlmStatusResponse status = jobMatchService.llmStatus();
        log.info("查询 LLM 状态: provider={}, model={}, hasApiKey={}",
                status.getProvider(), status.getModel(), status.isHasApiKey());
        return status;
    }

    @PostMapping("/llm/mode")
    public LlmStatusResponse setLlmMode(@RequestBody Map<String, String> body) {
        String mode = body.getOrDefault("mode", "auto");
        appProperties.getLlm().setMode(mode);
        log.info("LLM 模式切换: mode={}", mode);
        return jobMatchService.llmStatus();
    }

    @GetMapping("/jobs")
    public List<JobResponse> listJobs() {
        List<JobResponse> jobs = jobMatchService.listJobs();
        log.info("查询岗位列表: count={}", jobs.size());
        return jobs;
    }

    @PostMapping("/jobs")
    public JobResponse createJob(@Valid @RequestBody JobRequest req) {
        log.info("创建岗位: title={}", req.getTitle());
        JobResponse job = jobMatchService.createOrUpdate(null, req);
        log.info("创建岗位完成: id={}, title={}", job.getId(), job.getTitle());
        return job;
    }

    @PutMapping("/jobs/{id}")
    public JobResponse updateJob(@PathVariable Long id, @Valid @RequestBody JobRequest req) {
        log.info("更新岗位: id={}, title={}", id, req.getTitle());
        return jobMatchService.createOrUpdate(id, req);
    }

    @GetMapping("/jobs/{id}")
    public JobResponse getJob(@PathVariable Long id) {
        return jobMatchService.getJob(id);
    }

    @PostMapping(value = "/jobs/{id}/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<CandidateResponse> upload(@PathVariable Long id, @RequestParam("files") MultipartFile[] files) {
        long start = System.currentTimeMillis();
        int fileCount = files == null ? 0 : files.length;
        log.info("上传简历开始: jobId={}, fileCount={}", id, fileCount);
        List<CandidateResponse> result = jobMatchService.uploadResumes(id, Arrays.asList(files));
        log.info("上传简历完成: jobId={}, saved={}, costMs={}", id, result.size(), System.currentTimeMillis() - start);
        return result;
    }

    @GetMapping("/jobs/{id}/resumes")
    public List<CandidateResponse> listResumes(@PathVariable Long id) {
        List<CandidateResponse> list = jobMatchService.listCandidates(id);
        log.info("查询简历列表: jobId={}, count={}", id, list.size());
        // PRD R5: 脱敏
        list.forEach(this::maskCandidate);
        return list;
    }

    @PostMapping("/jobs/{id}/match")
    public List<MatchResponse> match(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        log.info("触发匹配开始: jobId={}", id);
        List<MatchResponse> result = jobMatchService.triggerMatch(id);
        log.info("触发匹配完成: jobId={}, reportCount={}, costMs={}",
                id, result.size(), System.currentTimeMillis() - start);
        return result;
    }

    @GetMapping("/jobs/{id}/matches")
    public List<MatchResponse> matches(@PathVariable Long id) {
        List<MatchResponse> list = jobMatchService.listMatches(id);
        log.info("查询匹配结果: jobId={}, count={}", id, list.size());
        return list;
    }

    @PostMapping("/jobs/{id}/invites")
    public List<MatchResponse> invite(@PathVariable Long id, @Valid @RequestBody InviteRequest req) {
        log.info("面试邀约: jobId={}, candidateIds={}", id, req.getCandidateIds());
        List<MatchResponse> list = jobMatchService.inviteCandidates(id, req.getCandidateIds());
        long invitedCount = list.stream().filter(MatchResponse::isInvited).count();
        log.info("面试邀约完成: jobId={}, invitedCount={}", id, invitedCount);
        return list;
    }

    @GetMapping("/candidates/{id}")
    public CandidateResponse candidate(@PathVariable Long id) {
        log.info("查询候选人: id={}", id);
        CandidateResponse c = jobMatchService.getCandidate(id);
        // PRD R5: 默认脱敏手机号/邮箱/身份证号
        maskCandidate(c);
        return c;
    }

    /**
     * PRD R5: HRBP 展开明文，记操作日志。
     */
    @GetMapping("/candidates/{id}/reveal")
    public CandidateResponse revealCandidate(@PathVariable Long id) {
        log.warn("【脱敏展开】候选人明文查阅: candidateId={}, 操作时间={}", id, java.time.Instant.now());
        return jobMatchService.getCandidate(id);
    }

    @GetMapping("/candidates/{id}/interview-pack")
    public MatchResponse interviewPack(@PathVariable Long id) {
        log.info("查询面试包: candidateId={}", id);
        return jobMatchService.getMatchForCandidate(id);
    }

    @PostMapping("/candidates/{id}/interview-evaluation")
    public MatchResponse saveInterviewEvaluation(
            @PathVariable Long id,
            @RequestBody InterviewEvaluationRequest req
    ) {
        log.info("保存面试评价: candidateId={}", id);
        MatchResponse result = jobMatchService.saveInterviewEvaluation(id, req);
        log.info("面试评价已锁定: candidateId={}, locked={}",
                id, result.getInterviewEvaluation() != null && result.getInterviewEvaluation().isLocked());
        return result;
    }

    @PostMapping("/demo/seed")
    public SeedResponse seed() throws Exception {
        long start = System.currentTimeMillis();
        log.info("加载样例数据开始");
        SeedResponse resp = demoSeedService.seed();
        log.info("加载样例数据已提交: jobId={}, candidateCount={}, status={}, costMs={}",
                resp.getJobId(), resp.getCandidateCount(), resp.getStatus(), System.currentTimeMillis() - start);
        return resp;
    }

    /**
     * PRD R5: 对候选人信息中的敏感字段进行脱敏。
     */
    private void maskCandidate(CandidateResponse c) {
        if (c == null || c.getProfile() == null) return;
        CandidateProfile p = c.getProfile();
        p.setName(maskName(p.getName()));
        if (p.getSummary() != null) p.setSummary(PrivacyUtil.maskSensitive(p.getSummary()));
        if (p.getHighlights() != null) {
            p.setHighlights(p.getHighlights().stream()
                    .map(PrivacyUtil::maskSensitive)
                    .toList());
        }
        if (p.getExperiences() != null) {
            p.getExperiences().forEach(e -> {
                if (e.getDescription() != null) e.setDescription(PrivacyUtil.maskSensitive(e.getDescription()));
                if (e.getTitle() != null) e.setTitle(PrivacyUtil.maskSensitive(e.getTitle()));
                if (e.getCompany() != null) e.setCompany(PrivacyUtil.maskSensitive(e.getCompany()));
            });
        }
    }

    /** 姓名脱敏：张三 → 张* */
    private static String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        return name.charAt(0) + "*" + (name.length() > 2 ? "*" : "");
    }
}
