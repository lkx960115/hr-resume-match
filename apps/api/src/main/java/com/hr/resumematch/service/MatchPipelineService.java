package com.hr.resumematch.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hr.resumematch.domain.*;
import com.hr.resumematch.dto.ApiDtos.*;
import com.hr.resumematch.llm.LlmRouter;
import com.hr.resumematch.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析/匹配流水线：LLM 调用在事务外执行，避免长时间占用 JDBC 连接导致 H2 回滚失败。
 */
@Slf4j
@Service
public class MatchPipelineService {

    private final CandidateRepository candidateRepository;
    private final MatchReportRepository matchReportRepository;
    private final JobRepository jobRepository;
    private final ResumeParseService resumeParseService;
    private final LlmRouter llmRouter;
    private final RiskDetector riskDetector;
    private final TransactionTemplate tx;

    public MatchPipelineService(
            CandidateRepository candidateRepository,
            MatchReportRepository matchReportRepository,
            JobRepository jobRepository,
            ResumeParseService resumeParseService,
            LlmRouter llmRouter,
            RiskDetector riskDetector,
            PlatformTransactionManager transactionManager
    ) {
        this.candidateRepository = candidateRepository;
        this.matchReportRepository = matchReportRepository;
        this.jobRepository = jobRepository;
        this.resumeParseService = resumeParseService;
        this.llmRouter = llmRouter;
        this.riskDetector = riskDetector;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public void parseCandidate(Long candidateId) {
        long start = System.currentTimeMillis();
        ParseCtx ctx = tx.execute(status -> {
            CandidateEntity c = candidateRepository.findById(candidateId).orElse(null);
            if (c == null) {
                return null;
            }
            JobEntity job = jobRepository.findById(c.getJobId()).orElseThrow();
            c.setParseStatus("PARSING");
            candidateRepository.saveAndFlush(c);
            return new ParseCtx(c.getId(), job.getTitle(), c.getStoredPath(), c.getFileName());
        });
        if (ctx == null) {
            log.warn("解析跳过: candidateId={} 不存在", candidateId);
            return;
        }

        try {
            log.info("解析开始: candidateId={}, file={}, provider={}",
                    candidateId, ctx.fileName(), llmRouter.resolvedProvider());
            String text = resumeParseService.extractText(Path.of(ctx.storedPath()), ctx.fileName());
            log.info("文本提取完成: candidateId={}, file={}, textLength={}",
                    candidateId, ctx.fileName(), text == null ? 0 : text.length());
            var profile = llmRouter.current().extractProfile(text, ctx.jobTitle());
            String profileJson = JsonUtils.toJson(profile);
            String risksJson = JsonUtils.toJson(riskDetector.detect(profile, text));
            tx.executeWithoutResult(status -> {
                CandidateEntity c = candidateRepository.findById(ctx.id()).orElseThrow();
                c.setRawText(text);
                c.setProfileJson(profileJson);
                c.setRiskFlagsJson(risksJson);
                c.setParseStatus("READY");
                c.setParseError(null);
                candidateRepository.save(c);
            });
            log.info("解析成功: candidateId={}, file={}, name={}, years={}, costMs={}",
                    candidateId,
                    ctx.fileName(),
                    profile == null ? null : profile.getName(),
                    profile == null ? null : profile.getYearsOfExperience(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("解析失败 candidate={}, file={}, costMs={}",
                    candidateId, ctx.fileName(), System.currentTimeMillis() - start, e);
            tx.executeWithoutResult(status -> {
                CandidateEntity c = candidateRepository.findById(ctx.id()).orElse(null);
                if (c == null) {
                    return;
                }
                c.setParseStatus("FAILED");
                c.setParseError(e.getMessage());
                candidateRepository.save(c);
            });
        }
    }

    public void matchOne(Long reportId, Long jobId) {
        long start = System.currentTimeMillis();
        MatchCtx ctx = tx.execute(status -> {
            MatchReportEntity report = matchReportRepository.findById(reportId).orElse(null);
            if (report == null) {
                return null;
            }
            JobEntity job = jobRepository.findById(jobId).orElseThrow();
            CandidateEntity c = candidateRepository.findById(report.getCandidateId()).orElseThrow();
            report.setStatus("RUNNING");
            matchReportRepository.saveAndFlush(report);

            Map<String, Object> jobPayload = new LinkedHashMap<>();
            jobPayload.put("title", job.getTitle());
            jobPayload.put("jdText", job.getJdText());
            jobPayload.put("hardRequirements", JsonUtils.fromJson(job.getHardRequirementsJson(), new TypeReference<List<HardRequirement>>() {}));
            jobPayload.put("dimensions", JsonUtils.fromJson(job.getDimensionsJson(), new TypeReference<List<DimensionWeight>>() {}));

            return new MatchCtx(
                    reportId,
                    c.getId(),
                    job.getTitle(),
                    JsonUtils.toJson(jobPayload),
                    c.getProfileJson(),
                    c.getRawText(),
                    c.getRiskFlagsJson()
            );
        });
        if (ctx == null) {
            log.warn("匹配跳过: reportId={} 不存在", reportId);
            return;
        }

        try {
            log.info("匹配打分开始: reportId={}, candidateId={}, jobId={}, provider={}",
                    reportId, ctx.candidateId(), jobId, llmRouter.resolvedProvider());
            MatchDetail detail = llmRouter.current().scoreMatch(ctx.jobJson(), ctx.profileJson(), ctx.resumeText());
            if (detail.getDimensions() != null) {
                for (DimensionScore d : detail.getDimensions()) {
                    d.setWeightedScore(Math.round(d.getScore() * d.getWeight() * 100.0) / 100.0);
                }
            }
            boolean pass = detail.getGateChecks() == null || detail.getGateChecks().stream().allMatch(GateCheck::isPassed);
            double total = detail.getDimensions() == null ? 0 :
                    detail.getDimensions().stream().mapToDouble(DimensionScore::getWeightedScore).sum();
            if (!pass) {
                total = Math.round(total * 0.5 * 100.0) / 100.0;
            }
            String summary = pass
                    ? "通过硬性门槛，综合加权分 " + total
                    : "未完全通过硬性门槛，综合分已降权为 " + total;

            log.info("生成面试包: reportId={}, candidateId={}, passHardGate={}, totalScore={}",
                    reportId, ctx.candidateId(), pass, total);
            InterviewPack pack = llmRouter.current().buildInterviewPack(
                    ctx.jobTitle(),
                    ctx.profileJson(),
                    summary,
                    ctx.risksJson()
            );

            final boolean passGate = pass;
            final double totalScore = total;
            final String summaryText = summary;
            final String detailJson = JsonUtils.toJson(detail);
            final String packJson = JsonUtils.toJson(pack);
            tx.executeWithoutResult(status -> {
                MatchReportEntity report = matchReportRepository.findById(ctx.reportId()).orElseThrow();
                report.setPassHardGate(passGate);
                report.setTotalScore(totalScore);
                report.setDetailJson(detailJson);
                report.setSummary(summaryText);
                report.setInterviewPackJson(packJson);
                report.setStatus("DONE");
                report.setErrorMessage(null);
                matchReportRepository.save(report);
            });
            log.info("匹配成功: reportId={}, candidateId={}, passHardGate={}, totalScore={}, costMs={}",
                    reportId, ctx.candidateId(), passGate, totalScore, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("匹配失败 reportId={}, candidateId={}, costMs={}",
                    reportId, ctx.candidateId(), System.currentTimeMillis() - start, e);
            tx.executeWithoutResult(status -> {
                MatchReportEntity report = matchReportRepository.findById(ctx.reportId()).orElse(null);
                if (report == null) {
                    return;
                }
                report.setStatus("FAILED");
                report.setErrorMessage(e.getMessage());
                matchReportRepository.save(report);
            });
        }
    }

    private record ParseCtx(Long id, String jobTitle, String storedPath, String fileName) {}

    private record MatchCtx(
            Long reportId,
            Long candidateId,
            String jobTitle,
            String jobJson,
            String profileJson,
            String resumeText,
            String risksJson
    ) {}
}
