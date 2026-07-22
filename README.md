# hr-resume-match

简历匹配与结构化面试助手 Demo（React + Spring Boot + OpenAI 兼容网关）。

产品需求文档：[docs/PRD.md](docs/PRD.md)

## 功能闭环

1. 配置岗位硬性门槛与能力权重  
2. 批量上传简历（PDF / DOCX / TXT）  
3. 可解释匹配排序（门槛 + 证据 + 权重）  
4. 生成风险点与结构化追问问题库  
5. 一键加载样例数据（无 Key 也可用 Mock 演示）

## 技术栈

| 层 | 选型 |
|---|---|
| 前端 | React + Vite + TypeScript + Ant Design |
| 后端 | Java 17 + Spring Boot 3 + Maven |
| 数据库 | H2（文件模式，本地可持久） |
| 解析 | PDFBox + Apache POI |
| LLM | OpenAI 兼容（默认 `hub.shhdpz.cn` / `gpt-5.5`）+ Mock 双模式 |

## 快速启动

### 0. 配置 LLM（推荐）

```bash
cp .env.example .env
# 编辑 .env，填入 OPENAI_API_KEY
```

`.env` 已被 gitignore，不会提交密钥。

### 1. 后端

```bash
cd apps/api
chmod +x run.sh
./run.sh
```

或手动导出环境变量后：

```bash
cd apps/api
export OPENAI_API_KEY=sk-你的密钥
export OPENAI_BASE_URL=https://hub.shhdpz.cn
export OPENAI_MODEL=gpt-5.5
mvn spring-boot:run
```

默认地址：http://localhost:8181  

Swagger：http://localhost:8181/swagger-ui.html  

### 2. 前端

```bash
cd apps/web
npm install
npm run dev
```

默认地址：http://localhost:5173  

### 3. 演示路径

1. 打开前端 → 点击「加载样例数据」  
2. 进入岗位「Java 后端工程师」→ 查看匹配结果榜  
3. 点开候选人 → 看证据拆解与面试追问题库  

页眉会显示当前 LLM：`openai / gpt-5.5`（无 Key 时为 `mock`）。

## 配置说明

| 变量 | 说明 | 默认 |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI 兼容网关 Key | 空 → Mock |
| `OPENAI_BASE_URL` | API 地址 | `https://hub.shhdpz.cn` |
| `OPENAI_MODEL` | 模型名 | `gpt-5.5` |
| `LLM_MODE` | `auto` / `openai` / `mock` | `auto` |

`auto`：有 Key 用 OpenAI 兼容接口，否则 Mock。  
兼容旧变量名：`DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL`。

## 目录

```
hr-resume-match/
├── apps/api/          # Spring Boot 后端
├── apps/web/          # React 前端
├── sample-data/       # 样例简历与岗位
├── docs/PRD.md        # 产品需求文档
├── .env.example       # 环境变量模板
└── README.md
```
