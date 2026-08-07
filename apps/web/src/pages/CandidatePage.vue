<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchCandidate, fetchInterviewPack, type Candidate, type MatchReport } from '../api'

const route = useRoute()
const id = computed(() => Number(route.params.candidateId))

const candidate = ref<Candidate | null>(null)
const report = ref<MatchReport | null>(null)

onMounted(() => {
  if (!id.value) return
  Promise.all([fetchCandidate(id.value), fetchInterviewPack(id.value)])
    .then(([c, r]) => {
      candidate.value = c
      report.value = r
    })
    .catch(() => ElMessage.error('加载候选人失败（请先完成匹配）'))
})

function severityType(severity: string) {
  if (severity === 'high') return 'danger'
  if (severity === 'medium') return 'warning'
  return 'info'
}

function severityClass(severity: string) {
  if (severity === 'high') return 'risk-high'
  if (severity === 'medium') return 'risk-medium'
  return 'risk-low'
}
</script>

<template>
  <div v-if="candidate" class="page-stack">
    <!-- Back Link -->
    <div class="page-sticky-bar">
      <RouterLink class="back-link" :to="`/jobs/${candidate.jobId}`">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="19" y1="12" x2="5" y2="12"/>
          <polyline points="12 19 5 12 12 5"/>
        </svg>
        返回岗位
      </RouterLink>
    </div>

    <!-- Candidate Header -->
    <div class="candidate-header fade-in">
      <div class="candidate-avatar">
        {{ (candidate.profile?.name || candidate.fileName || '?').charAt(0) }}
      </div>
      <div class="candidate-info">
        <h2 class="candidate-name">{{ candidate.profile?.name || candidate.fileName }}</h2>
        <div class="candidate-meta">
          <span class="meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 10v6M2 10l10-5 10 5-10 5z"/>
              <path d="M6 12v5c3 3 9 3 12 0v-5"/>
            </svg>
            {{ candidate.profile?.education || '-' }}
          </span>
          <span class="meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
            {{ candidate.profile?.yearsOfExperience ?? '-' }} 年经验
          </span>
        </div>
        <div class="candidate-skills" v-if="candidate.profile?.skills?.length">
          <el-tag v-for="s in candidate.profile.skills" :key="s" size="small" type="primary">
            {{ s }}
          </el-tag>
        </div>
        <p class="candidate-summary" v-if="candidate.profile?.summary">
          {{ candidate.profile.summary }}
        </p>
      </div>
    </div>

    <!-- Two Column Layout -->
    <div class="two-column">
      <!-- Risk Flags -->
      <div class="content-card fade-in">
        <div class="content-card-header">
          <h3 class="content-card-title">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
            风险点
          </h3>
          <el-tag v-if="candidate.riskFlags?.length" type="warning" size="small">
            {{ candidate.riskFlags.length }} 项
          </el-tag>
        </div>

        <div class="risk-list">
          <div
            v-for="(item, idx) in candidate.riskFlags || []"
            :key="`${item.type}-${idx}`"
            class="risk-item"
            :class="severityClass(item.severity)"
          >
            <div class="risk-header">
              <el-tag :type="severityType(item.severity)" size="small">
                {{ item.severity === 'high' ? '高' : item.severity === 'medium' ? '中' : '低' }}
              </el-tag>
              <span class="risk-type">{{ item.type }}</span>
            </div>
            <p class="risk-detail">{{ item.detail }}</p>
          </div>
        </div>

        <el-empty
          v-if="!(candidate.riskFlags && candidate.riskFlags.length)"
          description="暂无风险点"
          :image-size="80"
        />
      </div>

      <!-- Match Breakdown -->
      <div class="content-card fade-in">
        <div class="content-card-header">
          <h3 class="content-card-title">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="20" x2="18" y2="10"/>
              <line x1="12" y1="20" x2="12" y2="4"/>
              <line x1="6" y1="20" x2="6" y2="14"/>
            </svg>
            匹配拆解
          </h3>
          <div v-if="report" class="match-score-badge" :class="{ pass: report.passHardGate }">
            <span class="score-value">{{ report.totalScore?.toFixed(1) }}</span>
            <span class="score-label">{{ report.passHardGate ? '通过门槛' : '未过门槛' }}</span>
          </div>
        </div>

        <!-- Gate Checks -->
        <div class="gate-section">
          <div
            v-for="g in report?.detail?.gateChecks || []"
            :key="g.key"
            class="gate-item"
          >
            <div class="gate-icon" :class="{ passed: g.passed }">
              <svg v-if="g.passed" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </div>
            <span class="gate-label">{{ g.label }}</span>
            <span class="gate-detail">期望 {{ g.expected }} / 实际 {{ g.actual }}</span>
          </div>
        </div>

        <!-- Dimension Scores -->
        <div class="dimension-scores">
          <div
            v-for="d in report?.detail?.dimensions || []"
            :key="d.name"
            class="dimension-score-card"
          >
            <div class="dim-header">
              <span class="dim-name">{{ d.name }}</span>
              <div class="dim-score-info">
                <span class="dim-weight">权重 {{ Math.round(d.weight * 100) }}%</span>
                <span class="dim-score">{{ d.score.toFixed(0) }} → {{ d.weightedScore.toFixed(1) }}</span>
              </div>
            </div>
            <div class="dim-progress-bg">
              <div
                class="dim-progress-bar"
                :style="{ width: `${Math.round(d.score)}%` }"
              ></div>
            </div>
            <p class="dim-evidence">{{ d.evidence }}</p>
            <p class="dim-gap" v-if="d.gap">缺口：{{ d.gap }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Interview Questions -->
    <div class="content-card fade-in">
      <div class="content-card-header">
        <h3 class="content-card-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          结构化面试题库
        </h3>
      </div>

      <p class="interview-opening">{{ report?.interviewPack?.opening }}</p>

      <div class="question-list">
        <div
          v-for="(q, idx) in report?.interviewPack?.questions || []"
          :key="`${q.category}-${idx}`"
          class="question-item"
        >
          <div class="question-header">
            <span class="question-number">{{ idx + 1 }}</span>
            <el-tag size="small" type="primary">{{ q.category }}</el-tag>
          </div>
          <p class="question-text">{{ q.question }}</p>
          <div class="question-meta">
            <span v-if="q.intent" class="question-intent">
              <strong>意图：</strong>{{ q.intent }}
            </span>
            <span v-if="q.relatedRisk" class="question-risk">
              <strong>关联风险：</strong>{{ q.relatedRisk }}
            </span>
          </div>
        </div>
      </div>

      <div v-if="report?.interviewPack?.closingTips?.length" class="closing-section">
        <h4 class="closing-title">收尾提示</h4>
        <ul class="closing-list">
          <li v-for="t in report.interviewPack.closingTips" :key="t">{{ t }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
}

/* Back Link */
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: var(--transition);
}

.back-link:hover {
  color: var(--color-primary);
}

/* Candidate Header */
.candidate-header {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 28px;
}

.candidate-avatar {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-md);
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 28px;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: var(--shadow-primary);
}

.candidate-info {
  flex: 1;
  min-width: 0;
}

.candidate-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 10px;
  letter-spacing: -0.01em;
}

.candidate-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 14px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.candidate-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
}

.candidate-summary {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

/* Two Column Layout */
.two-column {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

@media (max-width: 900px) {
  .two-column {
    grid-template-columns: 1fr;
  }
}

/* Content Card */
.content-card {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 24px;
}

.content-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.content-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

/* Risk List */
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-item {
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border-light);
  background: var(--color-bg);
  transition: var(--transition);
}

.risk-item:hover {
  transform: translateX(4px);
}

.risk-item.risk-high {
  border-color: rgba(239, 68, 68, 0.3);
  background: var(--color-danger-bg);
}

.risk-item.risk-medium {
  border-color: rgba(245, 158, 11, 0.3);
  background: var(--color-warning-bg);
}

.risk-item.risk-low {
  border-color: var(--color-border);
}

.risk-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.risk-type {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.risk-detail {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

/* Match Score Badge */
.match-score-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
}

.match-score-badge.pass {
  background: var(--color-success-bg);
  border-color: rgba(16, 185, 129, 0.3);
}

.score-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.match-score-badge.pass .score-value {
  color: var(--color-success);
}

.score-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

/* Gate Section */
.gate-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border-light);
}

.gate-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}

.gate-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-danger-bg);
  color: var(--color-danger);
  flex-shrink: 0;
}

.gate-icon.passed {
  background: var(--color-success-bg);
  color: var(--color-success);
}

.gate-label {
  font-weight: 500;
  color: var(--color-text);
}

.gate-detail {
  color: var(--color-text-tertiary);
  font-size: 13px;
}

/* Dimension Scores */
.dimension-scores {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dimension-score-card {
  padding: 14px 16px;
  background: var(--color-bg);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
}

.dim-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.dim-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.dim-score-info {
  display: flex;
  gap: 12px;
  font-size: 12px;
}

.dim-weight {
  color: var(--color-text-tertiary);
}

.dim-score {
  color: var(--color-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.dim-progress-bg {
  height: 6px;
  background: var(--color-border-light);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 10px;
}

.dim-progress-bar {
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 3px;
  transition: width 0.4s ease-out;
}

.dim-evidence {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.dim-gap {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-danger);
}

/* Interview Questions */
.interview-opening {
  margin: 0 0 20px;
  padding: 16px;
  background: var(--color-primary-bg);
  border-radius: var(--radius-sm);
  color: var(--color-text);
  line-height: 1.6;
  font-size: 14px;
  border-left: 3px solid var(--color-primary);
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.question-item {
  padding: 16px;
  background: var(--color-bg);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  transition: var(--transition);
}

.question-item:hover {
  border-color: var(--color-primary);
  background: var(--color-bg-elevated);
}

.question-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.question-number {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--gradient-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.question-text {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--color-text);
  line-height: 1.5;
  font-weight: 500;
}

.question-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.question-intent strong,
.question-risk strong {
  color: var(--color-text-tertiary);
  font-weight: 600;
}

/* Closing Section */
.closing-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border-light);
}

.closing-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 12px;
}

.closing-list {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.closing-list li {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

/* Fade In Animation */
.fade-in {
  animation: fadeInUp 0.4s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
