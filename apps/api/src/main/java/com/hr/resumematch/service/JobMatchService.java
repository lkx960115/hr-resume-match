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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
    private final Executor taskExecutor;

    public JobMatchService(
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            MatchReportRepository matchReportRepository,
            ResumeParseService resumeParseService,
            MatchPipelineService matchPipelineService,
            LlmRouter llmRouter,
            PlatformTransactionManager transactionManager,
            Executor taskExecutor
    ) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.matchReportRepository = matchReportRepository;
        this.resumeParseService = resumeParseService;
        this.matchPipelineService = matchPipelineService;
        this.llmRouter = llmRouter;
        this.tx = new TransactionTemplate(transactionManager);
        this.taskExecutor = taskExecutor;
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
            if (!resumeParseService.isSupported(fileName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "不支持的文件格式：" + fileName + "，请上传 " + resumeParseService.supportFormats());
            }
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
            // 重新匹配前保留已邀约状态与已保存的面试评价（按 candidateId）
            Map<Long, Instant> invitedAtByCandidate = new HashMap<>();
            Map<Long, String> evaluationByCandidate = new HashMap<>();
            for (MatchReportEntity existing : matchReportRepository.findByJobIdOrderByTotalScoreDesc(jobId)) {
                if (existing.isInvited()) {
                    invitedAtByCandidate.put(
                            existing.getCandidateId(),
                            Optional.ofNullable(existing.getInvitedAt()).orElse(Instant.now()));
                }
                if (existing.getInterviewEvaluationJson() != null && !existing.getInterviewEvaluationJson().isBlank()) {
                    evaluationByCandidate.put(existing.getCandidateId(), existing.getInterviewEvaluationJson());
                }
            }
            matchReportRepository.deleteByJobId(jobId);
            List<Long> ids = new ArrayList<>();
            for (CandidateEntity c : candidates) {
                MatchReportEntity.MatchReportEntityBuilder builder = MatchReportEntity.builder()
                        .jobId(jobId)
                        .candidateId(c.getId())
                        .passHardGate(false)
                        .totalScore(0.0)
                        .status("PENDING");
                Instant invitedAt = invitedAtByCandidate.get(c.getId());
                if (invitedAt != null) {
                    builder.invited(true).invitedAt(invitedAt);
                }
                String evaluationJson = evaluationByCandidate.get(c.getId());
                if (evaluationJson != null) {
                    builder.interviewEvaluationJson(evaluationJson);
                }
                ids.add(matchReportRepository.save(builder.build()).getId());
            }
            log.info("匹配任务已创建: jobId={}, readyCandidates={}, reportCount={}, preservedInvites={}, preservedEvaluations={}",
                    jobId, candidates.size(), ids.size(), invitedAtByCandidate.size(), evaluationByCandidate.size());
            return ids;
        });
        // 并发匹配该岗位下的所有候选人；单个失败不影响整体。
        List<CompletableFuture<Void>> futures = reportIds.stream()
                .map(reportId -> CompletableFuture.runAsync(
                        () -> {
                            long start = System.currentTimeMillis();
                            log.info("匹配执行中: jobId={}, reportId={}", jobId, reportId);
                            matchPipelineService.matchOne(reportId, jobId);
                            log.info("匹配单人完成: jobId={}, reportId={}, costMs={}",
                                    jobId, reportId, System.currentTimeMillis() - start);
                        }, taskExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return listMatches(jobId);
    }

    /**
     * 批量发送面试邀约：仅允许已通过硬性门槛且存在匹配报告的候选人。
     * 已邀约的候选人视为幂等成功。
     */
    @Transactional
    public List<MatchResponse> inviteCandidates(Long jobId, List<Long> candidateIds) {
        requireJob(jobId);
        if (candidateIds == null || candidateIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择至少一位候选人");
        }
        List<Long> uniqueIds = candidateIds.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择至少一位候选人");
        }

        Map<Long, MatchReportEntity> reportMap = matchReportRepository
                .findByJobIdAndCandidateIdIn(jobId, uniqueIds).stream()
                .collect(Collectors.toMap(MatchReportEntity::getCandidateId, r -> r, (a, b) -> a));

        List<String> errors = new ArrayList<>();
        Instant now = Instant.now();
        int newlyInvited = 0;
        for (Long candidateId : uniqueIds) {
            MatchReportEntity report = reportMap.get(candidateId);
            if (report == null) {
                errors.add("候选人#" + candidateId + " 尚无匹配报告");
                continue;
            }
            if (!report.isPassHardGate()) {
                errors.add("候选人#" + candidateId + " 未通过硬性门槛，无法邀约");
                continue;
            }
            if (!report.isInvited()) {
                report.setInvited(true);
                report.setInvitedAt(now);
                matchReportRepository.save(report);
                newlyInvited++;
            }
        }
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("；", errors));
        }
        if (newlyInvited == 0 && uniqueIds.size() > 0) {
            log.info("面试邀约幂等完成: jobId={}, candidateIds={}, 全部已邀约", jobId, uniqueIds);
        } else {
            log.info("面试邀约完成: jobId={}, requested={}, newlyInvited={}", jobId, uniqueIds.size(), newlyInvited);
        }
        return listMatches(jobId);
    }

    /**
     * 保存面试官评价并锁定，不可再次编辑。
     */
    @Transactional
    public MatchResponse saveInterviewEvaluation(Long candidateId, InterviewEvaluationRequest req) {
        CandidateEntity c = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "候选人不存在"));
        MatchReportEntity report = matchReportRepository.findByJobIdAndCandidateId(c.getJobId(), candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "尚未生成匹配报告，请先触发匹配"));

        InterviewEvaluation existing = JsonUtils.fromJson(report.getInterviewEvaluationJson(), InterviewEvaluation.class);
        if (existing != null && existing.isLocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "面试评价已保存并锁定，不可再次编辑");
        }

        InterviewEvaluation evaluation = new InterviewEvaluation();
        evaluation.setScores(req.getScores() != null ? req.getScores() : new ArrayList<>());
        evaluation.setTotalScore(req.getTotalScore());
        evaluation.setRecommendation(req.getRecommendation());
        evaluation.setOverallComment(req.getOverallComment());
        evaluation.setInterviewDate(req.getInterviewDate());
        evaluation.setLocked(true);
        evaluation.setSavedAt(format(Instant.now()));

        report.setInterviewEvaluationJson(JsonUtils.toJson(evaluation));
        matchReportRepository.save(report);
        log.info("面试评价已保存锁定: candidateId={}, reportId={}, totalScore={}",
                candidateId, report.getId(), evaluation.getTotalScore());
        return toMatchResponse(report, c);
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
        r.setInvited(e.isInvited());
        r.setInvitedAt(format(e.getInvitedAt()));
        r.setDetail(JsonUtils.fromJson(e.getDetailJson(), MatchDetail.class));
        r.setInterviewPack(JsonUtils.fromJson(e.getInterviewPackJson(), InterviewPack.class));
        r.setInterviewEvaluation(JsonUtils.fromJson(e.getInterviewEvaluationJson(), InterviewEvaluation.class));
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
