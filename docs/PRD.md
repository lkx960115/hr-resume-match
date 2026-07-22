# 简历匹配与结构化面试助手 — 产品 PRD

| 字段 | 内容 |
|------|------|
| 项目名 | `hr-resume-match` |
| 类别 | 人力资源与组织发展 |
| 目标标签 | 效率提升 |
| 干系人 | 招聘、HRBP、用人单位 |
| 形态 | 前后端一体可演示 Demo |
| 版本 | v0.1（Demo） |

---

## 1. 背景与痛点

招聘侧简历量大、格式多样（PDF/Word/纯文本），人工初筛成本高；反馈口径不一，难以横向比较；面试准备依赖个人经验，追问点分散、难沉淀。

**本产品要解决：** 在岗位标准下批量初筛与排序，给出可解释匹配结果，并输出结构化面试追问题库。

---

## 2. 产品目标

上传脱敏简历与岗位标准，输出：

1. **匹配逻辑**（硬性门槛 + 能力证据 + 岗位权重）
2. **风险点**
3. **结构化追问问题库**

### Demo 成功标准

- 样例岗位 + 多份简历，可在短时间内出排序榜
- 每条结果可展开：门槛核验 / 维度分 / 证据摘录 / 缺口说明
- 候选人详情页可查看面试包（开场、问题、收尾提示）
- 全程本地可跑；无 Key 时可用 Mock 模式演示
- 有 Key 时可走 OpenAI 兼容接口（含 DeepSeek）

---

## 3. 用户与场景

| 角色 | 核心诉求 | Demo 中的动作 |
|------|----------|---------------|
| 招聘 | 快速筛掉明显不合格，拿到可比对名单 | 上传简历、调权重、看排序 |
| HRBP | 口径统一、结果可解释、可复盘 | 核对门槛、查看证据与报告 |
| 用人单位 | 准备面试、针对风险追问 | 打开候选人详情、使用面试题库 |

---

## 4. 范围定义

### P0 Must（本期必做）

| 能力 | 说明 |
|------|------|
| 岗位标准配置 | 硬性门槛（学历/年限/必会技能）+ 能力维度权重 |
| 批量简历上传与解析 | PDF / DOCX / TXT；解析失败可感知状态 |
| 可解释匹配排序 | 总分由维度分 × 权重聚合；未过门槛降权并标记 |
| 风险点 + 追问题库 | 按缺口/履历风险等生成结构化问题 |

### P1 Should（可增强）

| 能力 | 说明 |
|------|------|
| 对比视图 / 导出 | 双人对比；Markdown/PDF 面试包导出 |
| 任务进度与重跑 | 异步队列可视化；单份重跑；LLM 模式切换 UI |

### Out of Scope（本期不做）

- ATS / 招聘系统对接
- 视频面试、录音分析
- 完整权限体系 / 多租户计费
- 大规模向量库 RAG 检索

---

## 5. 核心流程

```
配置岗位 → 上传简历 → 解析结构化 → 匹配打分 → 结果榜 → 面试准备
```

| 步骤 | 输入 | 系统行为 | 输出 |
|------|------|----------|------|
| 1. 建岗 | JD / 手动字段 | 定义门槛与能力权重 | JobProfile |
| 2. 上传 | 多文件简历 | 落盘、解析、脱敏展示 | Candidate 草稿 |
| 3. 结构化 | 原文 + LLM/规则 | 抽教育/经历/技能/项目 | 结构化候选人 |
| 4. 匹配 | Job + Candidates | 规则门槛 + 证据评分 + 权重聚合 | MatchReport[] |
| 5. 面试准备 | MatchReport | 风险归类 → 追问问题库 | InterviewPack |

### 一键演示

`POST /api/demo/seed`：加载样例岗位「Java 后端工程师」+ 4 份样例简历，并自动完成匹配。

---

## 6. 匹配与可解释性规则

### 硬性门槛

- 示例：学历、工作年限、必会技能等
- **未通过**：仍可展示，但标记「未过门槛」，综合分降权，并说明缺口

### 能力证据（必须可解释）

每个维度输出：

- 分数（0–100）
- 权重与加权分
- **证据原文/命中说明**
- 缺口说明
- 置信度（可选）

**禁止只给总分、无法展开原因。**

### 演示稳定性

- LLM 输出按约定 JSON 解析；失败可降级 Mock/规则
- `LLM_MODE=auto`：有 API Key 用真实模型，否则 Mock
- 未配置 Key 时可用 Mock 完整演示；配置 Key 后走 OpenAI 兼容接口（含 DeepSeek）

---

## 7. 信息架构（页面）

| 页面 | 目的 | 关键交互 |
|------|------|----------|
| 首页 / 岗位列表 | 入口与演示 | 加载样例、进入岗位 |
| 岗位详情 | 标准与结果 | 门槛/权重、上传、匹配榜 |
| 候选人详情 | 深挖一人 | 画像、风险、维度拆解、面试题库 |

---

## 8. 技术选型（已定）

| 层 | 选型 |
|----|------|
| 前端 | React + Vite + TypeScript + Ant Design |
| 后端 | Java 17 + Spring Boot 3 + Maven |
| 数据库 | H2（文件模式，可切 PostgreSQL） |
| 简历解析 | Apache PDFBox + Apache POI |
| LLM | OpenAI 兼容网关（可接 DeepSeek 等）+ Mock 双模式 |
| 仓库路径 | `~/IdeaProjects/hr-resume-match` |

### LLM 说明

- `LLM_MODE=auto`：有 API Key 走真实模型，否则 Mock
- 支持 `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `OPENAI_MODEL`
- 兼容旧变量：`DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL`（可直接接 DeepSeek）

### 目录结构

```
hr-resume-match/
├── apps/api/          # Spring Boot 后端
├── apps/web/          # React 前端
├── sample-data/       # 样例简历
├── docs/PRD.md        # 本文档
└── README.md          # 启动说明
```

---

## 9. 系统架构（Demo）

| 层 | 组件 | 职责 |
|----|------|------|
| Web | Vite React App | 岗位、上传、榜单、详情、面试包 |
| API | Spring Boot | REST：jobs / resumes / matches / interview-packs |
| 领域服务 | Parse / Match / Interview | 抽取 → 结构化 → 打分 → 出题 |
| 存储 | H2 + 本地 uploads/ | 元数据、结构化 JSON、原始文件 |
| LLM | LlmRouter | openai 兼容 / mock 统一接口 |

---

## 10. API 最小集

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| GET | `/api/llm/status` | 当前 LLM 模式 |
| GET/POST | `/api/jobs` | 岗位列表 / 创建 |
| GET/PUT | `/api/jobs/{id}` | 岗位详情 / 更新 |
| POST | `/api/jobs/{id}/resumes` | 批量上传 |
| GET | `/api/jobs/{id}/resumes` | 简历解析状态列表 |
| POST | `/api/jobs/{id}/match` | 触发匹配 |
| GET | `/api/jobs/{id}/matches` | 排序结果 + 证据 |
| GET | `/api/candidates/{id}` | 结构化简历 + 风险 |
| GET | `/api/candidates/{id}/interview-pack` | 面试包（含匹配详情） |
| POST | `/api/demo/seed` | 加载样例并匹配 |

---

## 11. 数据模型（精简）

**Job**  
`id, title, jdText, hardRequirements[], dimensions[{name, weight}], createdAt`

**Candidate**  
`id, jobId, fileName, rawText, profileJson, parseStatus, riskFlags[]`

**MatchReport**  
`candidateId, passHardGate, totalScore, detail(gateChecks/dimensions), summary, interviewPack, status`

---

## 12. 里程碑建议

| 阶段 | 建议 | 交付物 |
|------|------|--------|
| M1 骨架 | 1–2 天 | Monorepo、上传、H2、Mock 通链路 |
| M2 解析+匹配 | 3–4 天 | 真实解析、可解释结果榜 |
| M3 面试包 | 2 天 | 风险与追问、详情页 |
| M4 演示打磨 | 2 天 | Seed、空态/失败态、README |

> 当前仓库已覆盖 M1–M4 的可演示闭环（Mock 默认可用）。

---

## 13. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 简历解析质量波动 | 字段缺失 | 状态可见；样例用纯文本兜底 |
| LLM 不稳定 / 无 Key | 演示翻车 | Mock 模式 + JSON 校验 |
| 「可解释」变黑盒 | 业务不信任 | 强制证据字段；门槛可视化 |
| 隐私合规 | 不能用真实简历 | Demo 仅脱敏样例；本地处理提示 |

---

## 14. 非功能要求（Demo 级）

- 本地双进程即可跑通（API `:8080` + Web `:5173`）
- 上传单文件建议 ≤ 20MB
- 接口超时前端按长任务预留（DeepSeek 场景）
- 日志可读，失败返回明确错误信息

---

## 15. 验收清单（Demo）

- [ ] 首页可一键 Seed
- [ ] 岗位页展示门槛与权重
- [ ] 匹配榜按门槛优先 + 分数排序
- [ ] 候选人页可见维度证据与面试题
- [ ] 无 Key 时显示 `mock`；配置 Key 后显示真实 provider
