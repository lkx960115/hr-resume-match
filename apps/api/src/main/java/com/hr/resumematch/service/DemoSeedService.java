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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 样例灌数：一次性创建多个岗位及对应简历，解析/匹配提交到线程池异步执行。
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
    private final Executor taskExecutor;

    public DemoSeedService(
            JobRepository jobRepository,
            CandidateRepository candidateRepository,
            MatchReportRepository matchReportRepository,
            ResumeParseService resumeParseService,
            MatchPipelineService matchPipelineService,
            JobMatchService jobMatchService,
            AppProperties props,
            PlatformTransactionManager transactionManager,
            Executor taskExecutor
    ) {
        this.jobRepository = jobRepository;
        this.candidateRepository = candidateRepository;
        this.matchReportRepository = matchReportRepository;
        this.resumeParseService = resumeParseService;
        this.matchPipelineService = matchPipelineService;
        this.jobMatchService = jobMatchService;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
        this.taskExecutor = taskExecutor;
    }

    public SeedResponse seed() throws Exception {
        long start = System.currentTimeMillis();
        log.info("样例灌数: 清空旧数据");
        tx.executeWithoutResult(status -> {
            matchReportRepository.deleteAll();
            candidateRepository.deleteAll();
            jobRepository.deleteAll();
        });

        Path uploadDir = resumeParseService.ensureUploadDir();
        List<JobConfig> configs = buildJobConfigs();

        Map<Long, List<Long>> jobCandidateMap = new LinkedHashMap<>();
        int totalCandidates = 0;

        for (JobConfig cfg : configs) {
            JobResponse job = jobMatchService.createOrUpdate(null, cfg.jobRequest);
            log.info("样例灌数: 已创建岗位 id={}, title={}", job.getId(), job.getTitle());

            List<Long> candidateIds = new ArrayList<>();
            for (String fileName : cfg.sampleFiles) {
                ClassPathResource res = new ClassPathResource("samples/" + fileName);
                String content = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Path target = uploadDir.resolve(fileName);
                Files.writeString(target, content, StandardCharsets.UTF_8);

                Long candidateId = tx.execute(status -> {
                    CandidateEntity c = CandidateEntity.builder()
                            .jobId(job.getId())
                            .fileName(fileName)
                            .storedPath(target.toAbsolutePath().toString())
                            .parseStatus("PENDING")
                            .build();
                    return candidateRepository.save(c).getId();
                });
                candidateIds.add(candidateId);
            }
            jobCandidateMap.put(job.getId(), candidateIds);
            totalCandidates += candidateIds.size();
            log.info("样例灌数: 岗位 {} 已创建 {} 份简历", job.getTitle(), candidateIds.size());
        }

        String modeLabel = switch (props.getLlm().getMode() == null ? "auto" : props.getLlm().getMode().trim().toLowerCase()) {
            case "mock" -> "mock（规则引擎）";
            case "openai", "deepseek" -> "openai/" + props.getLlm().getOpenai().getModel();
            default -> props.getLlm().getOpenai().getApiKey() != null && !props.getLlm().getOpenai().getApiKey().isBlank()
                    ? "openai/" + props.getLlm().getOpenai().getModel() : "mock（规则引擎）";
        };

        // 异步执行解析+匹配
        List<Long> allCandidateIds = jobCandidateMap.values().stream()
                .flatMap(List::stream)
                .toList();
        List<Long> allJobIds = new ArrayList<>(jobCandidateMap.keySet());
        taskExecutor.execute(() -> runAsyncSeed(allJobIds, allCandidateIds, modeLabel, start));

        SeedResponse resp = new SeedResponse();
        resp.setJobId(allJobIds.isEmpty() ? null : allJobIds.get(0));
        resp.setJobIds(allJobIds);
        resp.setCandidateCount(totalCandidates);
        resp.setStatus("RUNNING");
        resp.setMessage("已创建 " + configs.size() + " 个岗位、" + totalCandidates + " 份简历，正在后台解析并匹配（" + modeLabel + "），请稍后刷新岗位列表查看结果。");
        log.info("样例灌数已提交异步任务: jobs={}, candidates={}, costMs={}",
                allJobIds.size(), totalCandidates, System.currentTimeMillis() - start);
        return resp;
    }

    private void runAsyncSeed(List<Long> jobIds, List<Long> candidateIds, String modeLabel, long startAt) {
        try {
            log.info("样例灌数异步任务开始: jobs={}, candidates={}", jobIds.size(), candidateIds.size());

            // 等待主线程创建的数据在新事务中可见（H2 文件模式偶有延迟）
            waitForCandidatesVisible(candidateIds);

            // 1) 并发解析所有简历
            List<CompletableFuture<Void>> parseFutures = candidateIds.stream()
                    .map(id -> CompletableFuture.runAsync(
                            () -> {
                                long parseStart = System.currentTimeMillis();
                                matchPipelineService.parseCandidate(id);
                                log.info("样例灌数异步: 解析完成 candidateId={}, costMs={}",
                                        id, System.currentTimeMillis() - parseStart);
                            }, taskExecutor))
                    .toList();
            CompletableFuture.allOf(parseFutures.toArray(new CompletableFuture[0])).join();

            // 2) 逐个触发每个岗位的匹配（triggerMatch 内部已并发匹配候选人，无需外层再并行）
            //    避免外层并行占用线程池导致内部 matchOne 任务饥饿。
            log.info("样例灌数异步: 开始匹配 jobs={}", jobIds);
            for (Long jobId : jobIds) {
                long matchStart = System.currentTimeMillis();
                jobMatchService.triggerMatch(jobId);
                log.info("样例灌数异步: 匹配完成 jobId={}, costMs={}",
                        jobId, System.currentTimeMillis() - matchStart);
            }

            log.info("样例灌数异步任务完成: jobs={}, candidates={}, totalCostMs={}",
                    jobIds.size(), candidateIds.size(), System.currentTimeMillis() - startAt);
        } catch (Exception e) {
            log.error("样例灌数异步任务失败", e);
            tx.executeWithoutResult(status -> {
                for (Long candidateId : candidateIds) {
                    CandidateEntity c = candidateRepository.findById(candidateId).orElse(null);
                    if (c != null && !"READY".equals(c.getParseStatus())) {
                        c.setParseStatus("FAILED");
                        c.setParseError(e.getMessage());
                        candidateRepository.save(c);
                    }
                }
            });
        }
    }

    /**
     * 异步线程启动时，主线程事务可能尚未完全落盘到 H2 文件。
     * 轮询等待候选人在数据库中可见，避免后续解析读到空值。
     */
    private void waitForCandidatesVisible(List<Long> candidateIds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            long visible = tx.execute(status -> candidateIds.stream()
                    .filter(candidateRepository::existsById)
                    .count());
            if (visible == candidateIds.size()) {
                log.info("样例灌数异步: 所有 candidate 已可见, count={}", candidateIds.size());
                return;
            }
            log.info("样例灌数异步: 等待 candidate 可见 {}/{}", visible, candidateIds.size());
            TimeUnit.MILLISECONDS.sleep(200);
        }
        log.warn("样例灌数异步: 等待 candidate 可见超时，继续执行");
    }

    private static List<JobConfig> buildJobConfigs() {
        List<JobConfig> configs = new ArrayList<>();

        configs.add(new JobConfig(
                buildJavaJob(),
                new String[]{
                        "resume-zhangsan.txt",
                        "resume-lisi.txt",
                        "resume-wangwu.txt",
                        "resume-zhaoliu.txt"
                }
        ));

        configs.add(new JobConfig(
                buildFrontendJob(),
                new String[]{
                        "resume-fei-01.txt",
                        "resume-fei-02.txt",
                        "resume-fei-03.txt"
                }
        ));

        configs.add(new JobConfig(
                buildSeniorFrontendJob(),
                new String[]{
                        "resume-senior-fe-01.txt",
                        "resume-senior-fe-02.txt"
                }
        ));

        configs.add(new JobConfig(
                buildPmJob(),
                new String[]{
                        "resume-pm-01.txt",
                        "resume-pm-02.txt",
                        "resume-pm-03.txt"
                }
        ));

        configs.add(new JobConfig(
                buildAlgoJob(),
                new String[]{
                        "resume-algo-01.txt",
                        "resume-algo-02.txt",
                        "resume-algo-03.txt"
                }
        ));

        return configs;
    }

    private static JobRequest buildJavaJob() {
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
        dims.add(dim("Java/Spring 工程能力", "语言、框架、工程化", 0.30));
        dims.add(dim("数据与中间件", "MySQL/Redis/消息队列", 0.20));
        dims.add(dim("系统设计与稳定性", "高可用、排查、性能", 0.20));
        dims.add(dim("项目影响力", "项目规模、承担角色、量化成果", 0.15));
        dims.add(dim("协作与表达", "跨团队沟通、文档", 0.15));
        jobReq.setDimensions(dims);
        return jobReq;
    }

    private static JobRequest buildFrontendJob() {
        JobRequest jobReq = new JobRequest();
        jobReq.setTitle("前端工程师");
        jobReq.setJdText("""
                负责 Web / H5 / 小程序等前端产品研发。
                要求扎实的 HTML/CSS/JS 基础，熟练使用 React 或 Vue，具备组件化与工程化思维。
                关注性能、可维护性与用户体验，能与设计、后端高效协作。
                """);

        List<HardRequirement> hard = new ArrayList<>();
        hard.add(hr("education", "学历", "gte", "本科"));
        hard.add(hr("years", "工作年限", "gte", "2"));
        hard.add(hr("skill", "必会技能", "contains", "JavaScript"));
        jobReq.setHardRequirements(hard);

        List<DimensionWeight> dims = new ArrayList<>();
        dims.add(dim("前端基础与框架", "JS/TS、React/Vue", 0.30));
        dims.add(dim("工程化与性能", "构建工具、性能优化、测试", 0.25));
        dims.add(dim("组件与交互实现", "UI 还原、组件设计", 0.20));
        dims.add(dim("项目影响力", "项目规模、量化成果", 0.15));
        dims.add(dim("协作与表达", "沟通、文档、跨团队协作", 0.10));
        jobReq.setDimensions(dims);
        return jobReq;
    }

    private static JobRequest buildSeniorFrontendJob() {
        JobRequest jobReq = new JobRequest();
        jobReq.setTitle("高级前端工程师");
        jobReq.setJdText("""
                负责核心产品前端架构设计与技术攻关。
                要求精通 TypeScript、React/Vue 生态，具备微前端、SSR、性能优化等实战经验。
                能主导前端工程化建设，推动组件库、监控体系、自动化测试落地，具备技术影响力与带教能力。
                """);

        List<HardRequirement> hard = new ArrayList<>();
        hard.add(hr("education", "学历", "gte", "本科"));
        hard.add(hr("years", "工作年限", "gte", "5"));
        hard.add(hr("skill", "必会技能", "contains", "TypeScript"));
        jobReq.setHardRequirements(hard);

        List<DimensionWeight> dims = new ArrayList<>();
        dims.add(dim("前端架构与工程化", "微前端、SSR、构建体系", 0.30));
        dims.add(dim("框架深度与性能", "React/Vue 原理、性能调优", 0.25));
        dims.add(dim("质量保障体系", "测试策略、监控、CI/CD", 0.20));
        dims.add(dim("技术影响力", "组件库、规范制定、技术分享", 0.15));
        dims.add(dim("团队与协作", "带教、跨团队沟通、项目管理", 0.10));
        jobReq.setDimensions(dims);
        return jobReq;
    }

    private static JobRequest buildPmJob() {
        JobRequest jobReq = new JobRequest();
        jobReq.setTitle("产品经理");
        jobReq.setJdText("""
                负责产品规划、需求分析与项目推进。
                要求具备用户研究、数据分析和产品设计能力，能独立撰写 PRD 并推动落地。
                有良好的跨部门沟通能力和业务敏感度。
                """);

        List<HardRequirement> hard = new ArrayList<>();
        hard.add(hr("education", "学历", "gte", "本科"));
        hard.add(hr("years", "工作年限", "gte", "2"));
        hard.add(hr("skill", "必会技能", "contains", "需求分析"));
        jobReq.setHardRequirements(hard);

        List<DimensionWeight> dims = new ArrayList<>();
        dims.add(dim("需求分析与产品设计", "用户研究、原型、PRD", 0.30));
        dims.add(dim("数据驱动与增长", "指标体系、A/B 测试、增长", 0.25));
        dims.add(dim("项目推进能力", "跨团队协同、版本管理", 0.20));
        dims.add(dim("行业与业务理解", "领域知识、业务敏感度", 0.15));
        dims.add(dim("沟通与表达", "汇报、协调、文档", 0.10));
        jobReq.setDimensions(dims);
        return jobReq;
    }

    private static JobRequest buildAlgoJob() {
        JobRequest jobReq = new JobRequest();
        jobReq.setTitle("算法工程师");
        jobReq.setJdText("""
                负责机器学习、推荐或 NLP 相关算法研发与落地。
                要求扎实的数学与编程基础，熟悉主流深度学习框架，有模型上线与优化经验。
                能将算法模型转化为业务价值。
                """);

        List<HardRequirement> hard = new ArrayList<>();
        hard.add(hr("education", "学历", "gte", "本科"));
        hard.add(hr("years", "工作年限", "gte", "2"));
        hard.add(hr("skill", "必会技能", "contains", "Python"));
        jobReq.setHardRequirements(hard);

        List<DimensionWeight> dims = new ArrayList<>();
        dims.add(dim("算法与模型能力", "ML/DL、模型选型与调优", 0.30));
        dims.add(dim("工程与落地", "Python/C++、Spark、模型上线", 0.25));
        dims.add(dim("数据与特征", "数据处理、特征工程、指标评估", 0.20));
        dims.add(dim("项目影响力", "业务收益、论文专利", 0.15));
        dims.add(dim("协作与表达", "技术沟通、方案文档", 0.10));
        jobReq.setDimensions(dims);
        return jobReq;
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

    private record JobConfig(JobRequest jobRequest, String[] sampleFiles) {}
}
