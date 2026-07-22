package com.hr.resumematch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hr.resumematch.domain.*;
import com.hr.resumematch.dto.ApiDtos.*;
import com.hr.resumematch.llm.LlmRouter;
import com.hr.resumematch.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JobMatchService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final MatchReportRepository matchReportRepository;
    private final ResumeParseService resumeParseService;
    private final MatchPipelineService matchPipelineService;
    private final LlmRouter llmRouter;
    private final TransactionTemplate tx;

    public JobMatchService(
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            MatchReportRepository matchReportRepository,
            ResumeParseService resumeParseService,
            MatchPipelineService matchPipelineService,
            LlmRouter llmRouter,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.matchReportRepository = matchReportRepository;
        this.resumeParseService = resumeParseService;
        this.matchPipelineService = matchPipelineService;
        this.llmRouter = llmRouter;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public JobResponse createOrUpdate(Long id, JobRequest req) {
        normalizeWeights(req.getDimensions());
        JobEntity entity = id == null ? new JobEntity() : jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "岗位不存在"));
        entity.setTitle(req.getTitle());
        entity.setJdText(req.getJdText());
        entity.setHardRequirementsJson(JsonUtils.toJson(req.getHardRequirements()));
        entity.setDimensionsJson(JsonUtils.toJson(req.getDimensions()));
        return toJobResponse(jobRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<JobResponse> listJobs() {
        return jobRepository.findAll().stream().map(this::toJobResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(Long id) {
        return toJobResponse(requireJob(id));
    }

    public List<CandidateResponse> uploadResumes(Long jobId, List<MultipartFile> files) {
        requireJob(jobId);
        List<CandidateResponse> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            String fileName = file.getOriginalFilename();
            long start = System.currentTimeMillis();
            try {
                log.info("上传解析开始: jobId={}, file={}, size={}", jobId, fileName, file.getSize());
                var stored = resumeParseService.store(file);
                Long candidateId = tx.execute(status -> {
                    CandidateEntity c = CandidateEntity.builder()
                            .jobId(jobId)
                            .fileName(stored.originalName())
                            .storedPath(stored.storedPath())
                            .parseStatus("PENDING")
                            .build();
                    return candidateRepository.save(c).getId();
                });
                matchPipelineService.parseCandidate(candidateId);
                CandidateEntity saved = candidateRepository.findById(candidateId).orElseThrow();
                result.add(toCandidateResponse(saved));
                log.info("上传解析完成: jobId={}, candidateId={}, file={}, parseStatus={}, costMs={}",
                        jobId, candidateId, fileName, saved.getParseStatus(), System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("上传失败: jobId={}, file={}, costMs={}", jobId, fileName, System.currentTimeMillis() - start, e);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传失败: " + e.getMessage());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CandidateResponse> listCandidates(Long jobId) {
        requireJob(jobId);
        return candidateRepository.findByJobIdOrderByIdAsc(jobId).stream().map(this::toCandidateResponse).toList();
    }

    @Transactional(readOnly = true)
    public CandidateResponse getCandidate(Long id) {
        return toCandidateResponse(candidateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "候选人不存在")));
    }

    public List<MatchResponse> triggerMatch(Long jobId) {
        requireJob(jobId);
        List<Long> reportIds = tx.execute(status -> {
            List<CandidateEntity> candidates = candidateRepository.findByJobIdOrderByIdAsc(jobId).stream()
                    .filter(c -> "READY".equals(c.getParseStatus()))
                    .toList();
            if (candidates.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂无已解析完成的简历，请稍后再试或检查解析状态");
            }
            matchReportRepository.deleteByJobId(jobId);
            List<Long> ids = new ArrayList<>();
            for (CandidateEntity c : candidates) {
                MatchReportEntity r = MatchReportEntity.builder()
                        .jobId(jobId)
                        .candidateId(c.getId())
                        .passHardGate(false)
                        .totalScore(0.0)
                        .status("PENDING")
                        .build();
                ids.add(matchReportRepository.save(r).getId());
            }
            log.info("匹配任务已创建: jobId={}, readyCandidates={}, reportCount={}",
                    jobId, candidates.size(), ids.size());
            return ids;
        });
        int i = 0;
        for (Long reportId : reportIds) {
            i++;
            long start = System.currentTimeMillis();
            log.info("匹配执行中: jobId={}, reportId={}, progress={}/{}", jobId, reportId, i, reportIds.size());
            matchPipelineService.matchOne(reportId, jobId);
            log.info("匹配单人完成: jobId={}, reportId={}, costMs={}",
                    jobId, reportId, System.currentTimeMillis() - start);
        }
        return listMatches(jobId);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> listMatches(Long jobId) {
        requireJob(jobId);
        Map<Long, CandidateEntity> candMap = candidateRepository.findByJobIdOrderByIdAsc(jobId).stream()
                .collect(Collectors.toMap(CandidateEntity::getId, x -> x));
        return matchReportRepository.findByJobIdOrderByTotalScoreDesc(jobId).stream()
                .sorted((a, b) -> {
                    int g = Boolean.compare(b.isPassHardGate(), a.isPassHardGate());
                    if (g != 0) return g;
                    return Double.compare(
                            Optional.ofNullable(b.getTotalScore()).orElse(0.0),
                            Optional.ofNullable(a.getTotalScore()).orElse(0.0));
                })
                .map(r -> toMatchResponse(r, candMap.get(r.getCandidateId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatchForCandidate(Long candidateId) {
        CandidateEntity c = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "候选人不存在"));
        MatchReportEntity r = matchReportRepository.findByJobIdAndCandidateId(c.getJobId(), candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未生成匹配报告，请先触发匹配"));
        return toMatchResponse(r, c);
    }

    public LlmStatusResponse llmStatus() {
        LlmStatusResponse s = new LlmStatusResponse();
        s.setMode(llmRouter.hasApiKey() ? "ready" : "mock-fallback");
        s.setProvider(llmRouter.resolvedProvider());
        s.setHasApiKey(llmRouter.hasApiKey());
        s.setModel(llmRouter.resolvedModel());
        return s;
    }

    private void normalizeWeights(List<DimensionWeight> dims) {
        if (dims == null || dims.isEmpty()) return;
        double sum = dims.stream().mapToDouble(DimensionWeight::getWeight).sum();
        if (sum <= 0) {
            double each = 1.0 / dims.size();
            dims.forEach(d -> d.setWeight(each));
            return;
        }
        if (Math.abs(sum - 1.0) > 0.01) {
            for (DimensionWeight d : dims) {
                d.setWeight(Math.round(d.getWeight() / sum * 1000.0) / 1000.0);
            }
        }
    }

    private JobEntity requireJob(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "岗位不存在"));
    }

    private JobResponse toJobResponse(JobEntity e) {
        JobResponse r = new JobResponse();
        r.setId(e.getId());
        r.setTitle(e.getTitle());
        r.setJdText(e.getJdText());
        r.setHardRequirements(JsonUtils.fromJson(e.getHardRequirementsJson(), new TypeReference<>() {}));
        r.setDimensions(JsonUtils.fromJson(e.getDimensionsJson(), new TypeReference<>() {}));
        r.setCreatedAt(format(e.getCreatedAt()));
        return r;
    }

    CandidateResponse toCandidateResponse(CandidateEntity e) {
        CandidateResponse r = new CandidateResponse();
        r.setId(e.getId());
        r.setJobId(e.getJobId());
        r.setFileName(e.getFileName());
        r.setParseStatus(e.getParseStatus());
        r.setParseError(e.getParseError());
        r.setProfile(JsonUtils.fromJson(e.getProfileJson(), CandidateProfile.class));
        r.setRiskFlags(JsonUtils.fromJson(e.getRiskFlagsJson(), new TypeReference<>() {}));
        r.setCreatedAt(format(e.getCreatedAt()));
        return r;
    }

    private MatchResponse toMatchResponse(MatchReportEntity e, CandidateEntity c) {
        MatchResponse r = new MatchResponse();
        r.setId(e.getId());
        r.setJobId(e.getJobId());
        r.setCandidateId(e.getCandidateId());
        r.setPassHardGate(e.isPassHardGate());
        r.setTotalScore(e.getTotalScore());
        r.setSummary(e.getSummary());
        r.setStatus(e.getStatus());
        r.setDetail(JsonUtils.fromJson(e.getDetailJson(), MatchDetail.class));
        r.setInterviewPack(JsonUtils.fromJson(e.getInterviewPackJson(), InterviewPack.class));
        if (c != null) {
            r.setFileName(c.getFileName());
            CandidateProfile p = JsonUtils.fromJson(c.getProfileJson(), CandidateProfile.class);
            r.setCandidateName(p != null && p.getName() != null ? p.getName() : c.getFileName());
        }
        return r;
    }

    private String format(java.time.Instant instant) {
        if (instant == null) return null;
        return FMT.format(instant.atZone(ZoneId.systemDefault()));
    }
}
