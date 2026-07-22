package com.hr.resumematch.service;

import com.hr.resumematch.config.AppProperties;
import com.hr.resumematch.domain.*;
import com.hr.resumematch.dto.ApiDtos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 样例灌数：只做短事务写库，解析/匹配在事务外跑，避免 LLM 长时间占用连接。
 */
@Slf4j
@Service
public class DemoSeedService {

    private final JobRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final MatchReportRepository matchReportRepository;
    private final ResumeParseService resumeParseService;
    private final MatchPipelineService matchPipelineService;
    private final JobMatchService jobMatchService;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public DemoSeedService(
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            MatchReportRepository matchReportRepository,
            ResumeParseService resumeParseService,
            MatchPipelineService matchPipelineService,
            JobMatchService jobMatchService,
            AppProperties props,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.matchReportRepository = matchReportRepository;
        this.resumeParseService = resumeParseService;
        this.matchPipelineService = matchPipelineService;
        this.jobMatchService = jobMatchService;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public SeedResponse seed() throws Exception {
        long start = System.currentTimeMillis();
        log.info("样例灌数: 清空旧数据");
        tx.executeWithoutResult(status -> {
            matchReportRepository.deleteAll();
            candidateRepository.deleteAll();
            jobRepository.deleteAll();
        });

        JobRequest jobReq = new JobRequest();
        jobReq.setTitle("Java 后端工程师");
        jobReq.setJdText("""
                负责业务中台与核心交易链路的后端研发。
                要求扎实的 Java / Spring Boot 基础，熟悉 MySQL、Redis，有微服务协作经验优先。
                能独立排查线上问题，具备良好的沟通与文档能力。
                """);

        List<HardRequirement> hard = new ArrayList<>();
        hard.add(hr("education", "学历", "gte", "本科"));
        hard.add(hr("years", "工作年限", "gte", "3"));
        hard.add(hr("skill", "必会技能", "contains", "Java"));
        jobReq.setHardRequirements(hard);

        List<DimensionWeight> dims = new ArrayList<>();
        dims.add(dim("Java/Spring 工程能力", "语言、框架、工程化", 0.35));
        dims.add(dim("数据与中间件", "MySQL/Redis/消息队列", 0.25));
        dims.add(dim("系统设计与稳定性", "高可用、排查、性能", 0.25));
        dims.add(dim("协作与表达", "跨团队沟通、文档", 0.15));
        jobReq.setDimensions(dims);

        JobResponse job = jobMatchService.createOrUpdate(null, jobReq);
        log.info("样例灌数: 已创建岗位 id={}, title={}", job.getId(), job.getTitle());

        Path uploadDir = resumeParseService.ensureUploadDir();
        String[] samples = {
                "resume-zhangsan.txt",
                "resume-lisi.txt",
                "resume-wangwu.txt",
                "resume-zhaoliu.txt"
        };
        int count = 0;
        for (String name : samples) {
            long parseStart = System.currentTimeMillis();
            ClassPathResource res = new ClassPathResource("samples/" + name);
            String content = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Path target = uploadDir.resolve(name);
            Files.writeString(target, content, StandardCharsets.UTF_8);

            Long candidateId = tx.execute(status -> {
                CandidateEntity c = CandidateEntity.builder()
                        .jobId(job.getId())
                        .fileName(name)
                        .storedPath(target.toAbsolutePath().toString())
                        .parseStatus("PENDING")
                        .build();
                return candidateRepository.save(c).getId();
            });
            log.info("样例灌数: 解析简历 {}/{} file={}, candidateId={}", count + 1, samples.length, name, candidateId);
            matchPipelineService.parseCandidate(candidateId);
            log.info("样例灌数: 解析完成 file={}, candidateId={}, costMs={}",
                    name, candidateId, System.currentTimeMillis() - parseStart);
            count++;
        }

        log.info("样例灌数: 开始匹配 jobId={}, candidateCount={}", job.getId(), count);
        jobMatchService.triggerMatch(job.getId());

        SeedResponse resp = new SeedResponse();
        resp.setJobId(job.getId());
        resp.setCandidateCount(count);
        resp.setMessage("已加载样例岗位与 " + count + " 份简历，并完成匹配（LLM=" +
                (props.getLlm().getOpenai().getApiKey() == null || props.getLlm().getOpenai().getApiKey().isBlank()
                        ? "mock" : "openai/" + props.getLlm().getOpenai().getModel()) + "）");
        log.info("样例灌数结束: jobId={}, count={}, totalCostMs={}",
                job.getId(), count, System.currentTimeMillis() - start);
        return resp;
    }

    private static HardRequirement hr(String key, String label, String op, String value) {
        HardRequirement h = new HardRequirement();
        h.setKey(key);
        h.setLabel(label);
        h.setOperator(op);
        h.setValue(value);
        return h;
    }

    private static DimensionWeight dim(String name, String desc, double w) {
        DimensionWeight d = new DimensionWeight();
        d.setName(name);
        d.setDescription(desc);
        d.setWeight(w);
        return d;
    }
}
