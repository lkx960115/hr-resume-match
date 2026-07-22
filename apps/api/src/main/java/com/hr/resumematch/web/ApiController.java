package com.hr.resumematch.web;

import com.hr.resumematch.dto.ApiDtos.*;
import com.hr.resumematch.service.DemoSeedService;
import com.hr.resumematch.service.JobMatchService;
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

    @GetMapping("/candidates/{id}")
    public CandidateResponse candidate(@PathVariable Long id) {
        log.info("查询候选人: id={}", id);
        return jobMatchService.getCandidate(id);
    }

    @GetMapping("/candidates/{id}/interview-pack")
    public MatchResponse interviewPack(@PathVariable Long id) {
        log.info("查询面试包: candidateId={}", id);
        return jobMatchService.getMatchForCandidate(id);
    }

    @PostMapping("/demo/seed")
    public SeedResponse seed() throws Exception {
        long start = System.currentTimeMillis();
        log.info("加载样例数据开始");
        SeedResponse resp = demoSeedService.seed();
        log.info("加载样例数据完成: jobId={}, candidateCount={}, costMs={}, message={}",
                resp.getJobId(), resp.getCandidateCount(), System.currentTimeMillis() - start, resp.getMessage());
        return resp;
    }
}
