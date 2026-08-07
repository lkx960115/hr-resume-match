<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchInterviewPack,
  fetchJob,
  revealCandidate,
  saveInterviewEvaluation,
  type Candidate,
  type Job,
  type MatchReport,
} from '../api'

const route = useRoute()
const candidateId = computed(() => Number(route.params.candidateId))

const loading = ref(true)
const saving = ref(false)
const candidate = ref<Candidate | null>(null)
const job = ref<Job | null>(null)
const report = ref<MatchReport | null>(null)

type ScoreRow = {
  name: string
  weight: number
  score: number | null
  comment: string
}

const scoreRows = ref<ScoreRow[]>([])
const overallComment = ref('')
const interviewDate = ref(new Date().toISOString().slice(0, 10))
const recommendation = ref<'pass' | 'hold' | 'reject' | ''>('')
const locked = ref(false)
const savedAt = ref('')

const totalScore = computed(() => {
  const rows = scoreRows.value.filter((r) => r.score != null && !Number.isNaN(r.score))
  if (!rows.length) return null
  const weightSum = rows.reduce((s, r) => s + (r.weight || 0), 0)
  if (weightSum <= 0) {
    return Math.round((rows.reduce((s, r) => s + (r.score as number), 0) / rows.length) * 10) / 10
  }
  const weighted = rows.reduce((s, r) => s + (r.score as number) * (r.weight || 0), 0) / weightSum
  return Math.round(weighted * 10) / 10
})

const recommendationLabel = computed(() => {
  if (recommendation.value === 'pass') return '建议录用'
  if (recommendation.value === 'hold') return '待定 / 复试'
  if (recommendation.value === 'reject') return '不建议录用'
  return '—'
})

/** 邀约时间：与面试日期同风格，并带时分秒 → YYYY-MM-DD HH:mm:ss */
function formatDateTime(value?: string | null) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const invitedAtDisplay = computed(() => formatDateTime(report.value?.invitedAt))

function applyEvaluation(r: MatchReport) {
  const ev = r.interviewEvaluation
  if (!ev) return
  locked.value = !!ev.locked
  savedAt.value = ev.savedAt || ''
  if (ev.interviewDate) interviewDate.value = ev.interviewDate
  if (ev.recommendation === 'pass' || ev.recommendation === 'hold' || ev.recommendation === 'reject') {
    recommendation.value = ev.recommendation
  }
  overallComment.value = ev.overallComment || ''
  if (ev.scores?.length) {
    scoreRows.value = ev.scores.map((s) => ({
      name: s.name,
      weight: s.weight ?? 0,
      score: s.score ?? null,
      comment: s.comment || '',
    }))
  }
}

onMounted(async () => {
  if (!candidateId.value) return
  loading.value = true
  try {
    // reveal：面试评价表展示明文姓名（不脱敏）
    const [c, r] = await Promise.all([
      revealCandidate(candidateId.value),
      fetchInterviewPack(candidateId.value),
    ])
    candidate.value = c
    report.value = r
    const j = await fetchJob(c.jobId)
    job.value = j

    if (r.interviewEvaluation?.scores?.length) {
      applyEvaluation(r)
    } else {
      const dims =
        r.detail?.dimensions?.map((d) => ({
          name: d.name,
          weight: d.weight ?? 0,
          score: null as number | null,
          comment: '',
        })) ||
        j.dimensions?.map((d) => ({
          name: d.name,
          weight: d.weight ?? 0,
          score: null as number | null,
          comment: '',
        })) ||
        []
      scoreRows.value = dims.length
        ? dims
        : [
            { name: '专业能力', weight: 0.4, score: null, comment: '' },
            { name: '沟通协作', weight: 0.3, score: null, comment: '' },
            { name: '综合素质', weight: 0.3, score: null, comment: '' },
          ]
      if (r.interviewEvaluation) applyEvaluation(r)
    }
  } catch {
    ElMessage.error('加载面试详情失败（请确认已完成匹配）')
  } finally {
    loading.value = false
  }
})

function onPrint() {
  const prevTitle = document.title
  document.title = `${displayName.value} · 结构化面试评价表`
  const cleanup = () => {
    document.title = prevTitle
    window.removeEventListener('afterprint', cleanup)
  }
  window.addEventListener('afterprint', cleanup)
  window.print()
}

async function onSave() {
  if (!candidateId.value || locked.value) return
  try {
    await ElMessageBox.confirm(
      '保存后评价将锁定，不可再次编辑。确认保存？',
      '保存面试评价',
      { confirmButtonText: '确认保存', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  saving.value = true
  try {
    const updated = await saveInterviewEvaluation(candidateId.value, {
      scores: scoreRows.value.map((s) => ({
        name: s.name,
        weight: s.weight,
        score: s.score,
        comment: s.comment,
      })),
      totalScore: totalScore.value,
      recommendation: recommendation.value || undefined,
      overallComment: overallComment.value,
      interviewDate: interviewDate.value,
    })
    report.value = updated
    applyEvaluation(updated)
    ElMessage.success('保存成功，评价已锁定')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    ElMessage.error(err.response?.data?.error || err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const displayName = computed(
  () =>
    candidate.value?.profile?.name ||
    report.value?.candidateName ||
    candidate.value?.fileName ||
    `候选人#${candidateId.value}`,
)
</script>

<template>
  <div v-loading="loading" class="page-stack interview-page">
    <div class="page-sticky-bar toolbar no-print">
      <RouterLink v-if="candidate" class="back-link" :to="`/jobs/${candidate.jobId}`">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="19" y1="12" x2="5" y2="12" />
          <polyline points="12 19 5 12 12 5" />
        </svg>
        返回岗位
      </RouterLink>
      <el-button type="primary" :disabled="!candidate" @click="onPrint">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 6px;">
          <polyline points="6 9 6 2 18 2 18 9" />
          <path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2" />
          <rect x="6" y="14" width="12" height="8" />
        </svg>
        一键打印
      </el-button>
    </div>

    <div v-if="candidate && job" class="print-sheet">
      <header class="sheet-header">
        <div>
          <p class="sheet-eyebrow">结构化面试评价表</p>
          <h1 class="sheet-title">{{ displayName }} · 面试详情</h1>
        </div>
        <div class="sheet-meta">
          <div>面试日期：{{ interviewDate }}</div>
          <div v-if="invitedAtDisplay">邀约时间：{{ invitedAtDisplay }}</div>
          <div>匹配综合分：{{ report?.totalScore?.toFixed(1) ?? '—' }}</div>
        </div>
      </header>

      <!-- 1. 候选人基础信息 -->
      <section class="sheet-section">
        <h2 class="section-title">一、候选人基础信息</h2>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">姓名</span>
            <span class="info-value">{{ displayName }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">学历</span>
            <span class="info-value">{{ candidate.profile?.education || '—' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">工作年限</span>
            <span class="info-value">{{ candidate.profile?.yearsOfExperience ?? '—' }} 年</span>
          </div>
          <div class="info-item">
            <span class="info-label">简历文件</span>
            <span class="info-value">{{ candidate.fileName }}</span>
          </div>
          <div class="info-item full">
            <span class="info-label">技能标签</span>
            <span class="info-value">
              <template v-if="candidate.profile?.skills?.length">
                {{ candidate.profile.skills.join('、') }}
              </template>
              <template v-else>—</template>
            </span>
          </div>
          <div class="info-item full" v-if="candidate.profile?.summary">
            <span class="info-label">简历摘要</span>
            <span class="info-value">{{ candidate.profile.summary }}</span>
          </div>
        </div>
      </section>

      <!-- 2. 岗位信息 -->
      <section class="sheet-section">
        <h2 class="section-title">二、面试岗位信息</h2>
        <div class="info-grid">
          <div class="info-item full">
            <span class="info-label">岗位名称</span>
            <span class="info-value">{{ job.title }}</span>
          </div>
          <div class="info-item full" v-if="job.jdText">
            <span class="info-label">岗位描述</span>
            <span class="info-value pre-wrap">{{ job.jdText }}</span>
          </div>
          <div class="info-item full" v-if="job.hardRequirements?.length">
            <span class="info-label">硬性门槛</span>
            <span class="info-value">
              <ul class="plain-list">
                <li v-for="h in job.hardRequirements" :key="h.key">
                  {{ h.label }}：{{ h.operator }} {{ h.value }}
                </li>
              </ul>
            </span>
          </div>
        </div>
      </section>

      <!-- 3. 面试官综合评分 -->
      <section class="sheet-section">
        <h2 class="section-title">三、面试官综合评分</h2>
        <p class="section-hint no-print">请按维度打分（0–100），系统按权重汇总；保存后将锁定不可修改。</p>
        <table class="score-table">
          <thead>
            <tr>
              <th style="width: 22%">评价维度</th>
              <th style="width: 10%">权重</th>
              <th style="width: 14%">得分</th>
              <th>评语 / 依据</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in scoreRows" :key="row.name">
              <td>{{ row.name }}</td>
              <td>{{ Math.round((row.weight || 0) * 100) }}%</td>
              <td>
                <input
                  v-model.number="row.score"
                  class="score-input"
                  type="number"
                  min="0"
                  max="100"
                  step="1"
                  placeholder="—"
                  :disabled="locked"
                />
              </td>
              <td>
                <input
                  v-model="row.comment"
                  class="comment-input"
                  type="text"
                  placeholder="填写评语"
                  :disabled="locked"
                />
              </td>
            </tr>
          </tbody>
        </table>

        <div class="score-summary">
          <div class="summary-item">
            <span class="info-label">加权综合分</span>
            <span class="summary-score">{{ totalScore ?? '—' }}</span>
          </div>
          <div class="summary-item grow">
            <span class="info-label">录用建议</span>
            <div class="reco-group no-print">
              <label>
                <input v-model="recommendation" type="radio" value="pass" :disabled="locked" />
                建议录用
              </label>
              <label>
                <input v-model="recommendation" type="radio" value="hold" :disabled="locked" />
                待定 / 复试
              </label>
              <label>
                <input v-model="recommendation" type="radio" value="reject" :disabled="locked" />
                不建议录用
              </label>
            </div>
            <span class="print-only reco-print">{{ recommendationLabel }}</span>
          </div>
        </div>

        <div class="overall-box">
          <span class="info-label">综合评价</span>
          <textarea
            v-model="overallComment"
            class="overall-input"
            rows="4"
            placeholder="请填写面试整体评价、亮点与风险…"
            :disabled="locked"
          />
        </div>
      </section>

      <!-- 4. 面试官签字：仅打印呈现 -->
      <section class="sheet-section signature-section print-only-block">
        <h2 class="section-title">四、面试官签字</h2>
        <div class="signature-grid">
          <div class="sig-field full">
            <span class="info-label">签字确认</span>
            <div class="signature-line">
              <span class="sig-placeholder">（签字）</span>
            </div>
          </div>
          <div class="sig-field">
            <span class="info-label">面试日期</span>
            <span class="sig-date-text">{{ interviewDate }}</span>
          </div>
        </div>
        <p class="sig-note">本人确认上述评价客观、真实，并对面试结论负责。</p>
      </section>
    </div>

    <!-- 屏幕态：保存 / 已锁定 -->
    <div v-if="candidate && job" class="save-bar no-print">
      <div class="save-meta">
        <label class="save-date-field">
          <span class="info-label">面试日期</span>
          <input
            v-model="interviewDate"
            class="sig-input"
            type="date"
            :disabled="locked"
          />
        </label>
        <span v-if="locked" class="locked-hint">
          已保存锁定{{ savedAt ? ` · ${savedAt}` : '' }}
        </span>
      </div>
      <el-button
        v-if="!locked"
        type="primary"
        size="large"
        :loading="saving"
        @click="onSave"
      >
        保存
      </el-button>
      <el-button v-else type="info" size="large" disabled>
        已保存，不可编辑
      </el-button>
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

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
}

.back-link:hover {
  color: var(--color-primary);
}

.print-sheet {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 28px 32px 36px;
}

.sheet-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 20px;
  border-bottom: 2px solid var(--color-text);
  margin-bottom: 24px;
}

.sheet-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.sheet-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
}

.sheet-meta {
  text-align: right;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  flex-shrink: 0;
}

.sheet-section {
  margin-bottom: 28px;
}

.section-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--color-border);
}

.section-hint {
  margin: -4px 0 12px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.sub-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item.full {
  grid-column: 1 / -1;
}

.info-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-tertiary);
}

.info-value {
  font-size: 14px;
  color: var(--color-text);
  line-height: 1.55;
}

.pre-wrap {
  white-space: pre-wrap;
}

.plain-list {
  margin: 0;
  padding-left: 18px;
}

.pack-opening {
  margin: 0 0 14px;
  padding: 12px 14px;
  background: var(--color-primary-bg);
  border-radius: var(--radius-sm);
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.question-item {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 14px 16px;
}

.question-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.q-num {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--color-primary-light);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.q-cat {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary);
}

.q-text {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.55;
}

.q-meta {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.answer-space {
  margin-top: 10px;
  min-height: 48px;
  border-bottom: 1px dashed var(--color-border);
}

.answer-label {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.empty-hint {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-tertiary);
}

.score-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.score-table th,
.score-table td {
  border: 1px solid var(--color-border);
  padding: 10px 12px;
  text-align: left;
  vertical-align: middle;
}

.score-table th {
  background: var(--color-bg);
  font-size: 12px;
  color: var(--color-text-secondary);
  font-weight: 600;
}

.score-input,
.comment-input,
.sig-input,
.overall-input {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  padding: 6px 8px;
  font: inherit;
  color: var(--color-text);
  background: #fff;
}

.score-input {
  max-width: 88px;
}

.score-summary {
  display: flex;
  gap: 24px;
  margin-top: 16px;
  align-items: flex-start;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-item.grow {
  flex: 1;
}

.summary-score {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1;
}

.reco-group {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 14px;
  color: var(--color-text);
}

.reco-group label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.overall-box {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.overall-input {
  resize: vertical;
  min-height: 96px;
  line-height: 1.55;
}

.signature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 24px;
}

.sig-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sig-field.full {
  grid-column: 1 / -1;
}

.signature-line {
  min-height: 64px;
  border-bottom: 1.5px solid var(--color-text);
  display: flex;
  align-items: flex-end;
  padding-bottom: 6px;
}

.sig-placeholder {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.sig-note {
  margin: 16px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.print-only {
  display: none;
}

.print-only-block {
  display: none;
}

.sig-date-text {
  font-size: 14px;
  color: var(--color-text);
  padding: 6px 0;
  border-bottom: 1px solid #ccc;
}

.save-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.save-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 16px;
}

.save-date-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 180px;
}

.locked-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  padding-bottom: 8px;
}

.score-input:disabled,
.comment-input:disabled,
.overall-input:disabled,
.sig-input:disabled {
  background: var(--color-bg) !important;
  color: var(--color-text) !important;
  cursor: not-allowed;
  opacity: 0.85;
}

.reco-group label:has(input:disabled) {
  opacity: 0.7;
  cursor: not-allowed;
}

@media print {
  .no-print {
    display: none !important;
  }

  .print-only {
    display: inline;
  }

  .print-only-block {
    display: block !important;
  }

  .sig-date-text {
    display: block;
  }

  .page-stack {
    gap: 0 !important;
  }

  .print-sheet {
    border: none !important;
    border-radius: 0 !important;
    padding: 0 !important;
    box-shadow: none !important;
    background: #fff !important;
  }

  .sheet-header {
    display: flex !important;
    align-items: flex-start;
    gap: 16px !important;
    margin-bottom: 10px !important;
    padding-bottom: 8px !important;
    border-bottom: 1.5px solid #111 !important;
  }

  .sheet-eyebrow {
    font-size: 9pt !important;
    margin-bottom: 2px !important;
    color: #555 !important;
  }

  .sheet-title {
    font-size: 14pt !important;
    line-height: 1.25 !important;
  }

  .sheet-meta {
    font-size: 9pt !important;
    line-height: 1.45 !important;
    color: #333 !important;
  }

  .sheet-section {
    margin-bottom: 10px !important;
  }

  .section-title {
    font-size: 11pt !important;
    margin: 0 0 6px !important;
    padding-bottom: 3px !important;
    border-bottom: 1px solid #999 !important;
    color: #111 !important;
  }

  .info-grid {
    gap: 3px 14px !important;
  }

  .info-label {
    font-size: 8pt !important;
    color: #666 !important;
  }

  .info-value,
  .q-text,
  .plain-list,
  .sig-note {
    font-size: 9.5pt !important;
    line-height: 1.35 !important;
    color: #111 !important;
  }

  .pre-wrap {
    white-space: pre-wrap;
    max-height: 4.8em;
    overflow: hidden;
  }

  .pack-opening {
    background: transparent !important;
    border: 1px solid #bbb !important;
    border-radius: 0 !important;
    padding: 5px 8px !important;
    margin: 0 0 6px !important;
    font-size: 9pt !important;
    color: #333 !important;
  }

  .question-list {
    gap: 5px !important;
  }

  .question-item {
    border: 1px solid #bbb !important;
    border-radius: 0 !important;
    padding: 5px 8px !important;
    background: #fff !important;
  }

  .question-head {
    margin-bottom: 3px !important;
  }

  .q-num {
    width: 16px !important;
    height: 16px !important;
    border-radius: 0 !important;
    background: transparent !important;
    border: 1px solid #333 !important;
    color: #111 !important;
    font-size: 8pt !important;
  }

  .q-cat {
    color: #111 !important;
    font-size: 8.5pt !important;
  }

  .q-text {
    margin-bottom: 2px !important;
  }

  .q-meta {
    margin: 0 !important;
    font-size: 8pt !important;
    color: #444 !important;
  }

  .answer-space {
    min-height: 18px !important;
    margin-top: 3px !important;
    border-bottom: 1px solid #ccc !important;
  }

  .answer-label {
    font-size: 7.5pt !important;
    color: #888 !important;
  }

  .closing-tips {
    margin-top: 4px !important;
  }

  .sub-title {
    margin: 6px 0 4px !important;
    font-size: 9.5pt !important;
  }

  .score-table {
    font-size: 9pt !important;
  }

  .score-table th,
  .score-table td {
    padding: 4px 6px !important;
    border-color: #999 !important;
  }

  .score-table th {
    background: #eee !important;
    color: #333 !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .score-table tr {
    break-inside: avoid;
  }

  .score-input,
  .comment-input,
  .sig-input,
  .overall-input {
    border: none !important;
    border-bottom: 1px solid #999 !important;
    border-radius: 0 !important;
    padding: 2px 0 !important;
    background: transparent !important;
    box-shadow: none !important;
    color: #111 !important;
    font-size: 9.5pt !important;
  }

  .overall-input {
    min-height: 48px !important;
    border: 1px solid #999 !important;
    border-bottom: 1px solid #999 !important;
    padding: 4px 6px !important;
  }

  .score-summary {
    margin-top: 8px !important;
    gap: 16px !important;
  }

  .summary-score {
    font-size: 16pt !important;
    color: #111 !important;
  }

  .overall-box {
    margin-top: 8px !important;
  }

  .signature-section {
    break-inside: avoid;
    page-break-inside: avoid;
    margin-top: 8px !important;
  }

  .signature-grid {
    gap: 8px 16px !important;
  }

  .signature-line {
    min-height: 36px !important;
  }

  .sig-note {
    margin-top: 8px !important;
  }
}

@media (max-width: 720px) {
  .sheet-header,
  .info-grid,
  .signature-grid,
  .score-summary {
    flex-direction: column;
    grid-template-columns: 1fr;
  }

  .sheet-meta {
    text-align: left;
  }
}
</style>

<style>
@media print {
  @page {
    size: A4;
    margin: 10mm 12mm;
  }

  html,
  body {
    background: #fff !important;
    color: #111 !important;
  }

  .navbar,
  .no-print,
  .back-top-btn,
  .el-loading-mask {
    display: none !important;
  }

  .app-shell,
  .main-container {
    display: block !important;
    max-width: none !important;
    width: 100% !important;
    padding: 0 !important;
    margin: 0 !important;
    min-height: 0 !important;
    background: #fff !important;
  }

  /* 避免页面底部被撑出大块空白 */
  .interview-page,
  .interview-page .print-sheet {
    min-height: 0 !important;
  }
}
</style>
