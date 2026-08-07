<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRawFile } from 'element-plus'
import {
  fetchCandidates,
  fetchJob,
  fetchMatches,
  inviteToInterview,
  triggerMatch,
  uploadResumes,
  type Candidate,
  type Job,
  type MatchReport,
} from '../api'

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.jobId))

const job = ref<Job | null>(null)
const candidates = ref<Candidate[]>([])
const matches = ref<MatchReport[]>([])
const uploading = ref(false)
const matching = ref(false)
const inviting = ref(false)
const selectedRows = ref<MatchReport[]>([])
const matchTableRef = ref<{ clearSelection: () => void } | null>(null)
let uploadingLock = false
let pendingUploadFiles: File[] = []
let uploadFlushTimer: ReturnType<typeof setTimeout> | null = null

const passedMatches = computed(() => matches.value.filter((m) => m.passHardGate))
const selectedInviteable = computed(() =>
  selectedRows.value.filter((m) => m.passHardGate && !m.invited),
)
const canBatchInvite = computed(() => selectedInviteable.value.length > 0)
/** 邀约状态变化时强制刷新表格选择列（Element Plus selectable 不会自动重算） */
const matchTableKey = computed(() =>
  matches.value.filter((m) => m.invited).map((m) => m.candidateId).sort((a, b) => a - b).join(','),
)

async function reload() {
  if (!id.value) return
  const [j, c, m] = await Promise.all([
    fetchJob(id.value),
    fetchCandidates(id.value),
    fetchMatches(id.value),
  ])
  job.value = j
  candidates.value = c
  matches.value = m
  selectedRows.value = []
}

onMounted(() => {
  reload().catch(() => ElMessage.error('加载岗位失败'))
})

async function onUploadFiles(files: File[]) {
  if (!files.length || uploadingLock) return
  uploadingLock = true
  uploading.value = true
  try {
    await uploadResumes(id.value, files)
    await reload()
    ElMessage.success(`已上传并解析 ${files.length} 份简历`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    ElMessage.error(err.response?.data?.error || err.message || '上传失败')
  } finally {
    uploadingLock = false
    uploading.value = false
  }
}

function beforeUpload(file: UploadRawFile) {
  pendingUploadFiles.push(file)
  if (uploadFlushTimer) clearTimeout(uploadFlushTimer)
  uploadFlushTimer = setTimeout(() => {
    const batch = [...pendingUploadFiles]
    pendingUploadFiles = []
    uploadFlushTimer = null
    void onUploadFiles(batch)
  }, 0)
  return false
}

async function onMatch() {
  matching.value = true
  try {
    await triggerMatch(id.value)
    await reload()
    ElMessage.success('匹配完成')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } } }
    ElMessage.error(err.response?.data?.error || '匹配失败')
  } finally {
    matching.value = false
  }
}

function candidateLabel(row: MatchReport) {
  return row.candidateName || row.fileName || `候选人#${row.candidateId}`
}

function isInvited(row: MatchReport) {
  return !!row.invited
}

function onSelectionChange(rows: MatchReport[]) {
  selectedRows.value = rows
}

function selectableRow(row: MatchReport) {
  return row.passHardGate && !row.invited
}

async function inviteCandidates(rows: MatchReport[]) {
  const targets = rows.filter((r) => r.passHardGate && !r.invited)
  if (!targets.length) {
    ElMessage.warning('没有可邀约的候选人（需已通过门槛且未邀约）')
    return
  }

  const names = targets.map(candidateLabel).join('、')
  try {
    await ElMessageBox.confirm(
      targets.length === 1
        ? `确认向「${names}」发送面试邀约？`
        : `确认向以下 ${targets.length} 位通过门槛的候选人发送面试邀约？\n${names}`,
      '邀约面试',
      {
        confirmButtonText: '确认邀约',
        cancelButtonText: '取消',
        type: 'info',
      },
    )
  } catch {
    return
  }

  inviting.value = true
  try {
    const updated = await inviteToInterview(
      id.value,
      targets.map((t) => t.candidateId),
    )
    matches.value = updated
    selectedRows.value = []
    matchTableRef.value?.clearSelection()
    ElMessage.success(
      targets.length === 1
        ? `已向「${names}」发送面试邀约`
        : `已向 ${targets.length} 位候选人发送面试邀约`,
    )
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    ElMessage.error(err.response?.data?.error || err.message || '邀约失败')
  } finally {
    inviting.value = false
  }
}

function onInviteOne(row: MatchReport) {
  void inviteCandidates([row])
}

function onBatchInvite() {
  void inviteCandidates(selectedInviteable.value)
}
</script>

<template>
  <div v-if="job" class="page-stack">
    <!-- Back Link -->
    <div class="page-sticky-bar">
      <RouterLink class="back-link" to="/">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="19" y1="12" x2="5" y2="12"/>
          <polyline points="12 19 5 12 12 5"/>
        </svg>
        返回岗位列表
      </RouterLink>
    </div>

    <!-- Job Info Card -->
    <div class="job-info-card fade-in">
      <div class="job-info-header">
        <div>
          <h2 class="job-info-title">{{ job.title }}</h2>
          <p class="job-info-desc">{{ job.jdText }}</p>
        </div>
      </div>

      <!-- Hard Requirements -->
      <div class="info-section">
        <h4 class="info-section-title">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 11l3 3L22 4"/>
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
          </svg>
          硬性门槛
        </h4>
        <div class="threshold-list">
          <div v-for="h in job.hardRequirements" :key="h.key" class="threshold-item">
            <span class="threshold-label">{{ h.label }}</span>
            <span class="threshold-value">{{ h.operator }} {{ h.value }}</span>
          </div>
        </div>
      </div>

      <!-- Dimensions -->
      <div class="info-section">
        <h4 class="info-section-title">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="20" x2="18" y2="10"/>
            <line x1="12" y1="20" x2="12" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="14"/>
          </svg>
          能力权重分布
        </h4>
        <div class="dimension-grid">
          <div v-for="d in job.dimensions" :key="d.name" class="dimension-card">
            <div class="dimension-header">
              <span class="dimension-name">{{ d.name }}</span>
              <span class="dimension-weight">{{ Math.round(d.weight * 100) }}%</span>
            </div>
            <div class="dimension-bar-bg">
              <div
                class="dimension-bar"
                :style="{ width: `${Math.round(d.weight * 100)}%` }"
              ></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Resumes Section -->
    <div class="content-card fade-in">
      <div class="content-card-header">
        <h3 class="content-card-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          简历管理
        </h3>
        <div class="content-card-actions">
          <el-upload
            multiple
            accept=".pdf,.docx,.txt,.md"
            :show-file-list="false"
            :before-upload="beforeUpload"
          >
            <el-button :loading="uploading" :disabled="matching">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px;">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              上传简历
            </el-button>
          </el-upload>
          <el-button type="primary" :loading="matching" :disabled="uploading" @click="onMatch">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px;">
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
            重新匹配
          </el-button>
        </div>
      </div>

      <el-table :data="candidates" size="small" row-key="id" class="styled-table">
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column label="姓名" width="120">
          <template #default="{ row }">
            <span class="candidate-name">{{ row.profile?.name || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="parseStatus" label="解析状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.parseStatus === 'completed' ? 'success' : 'info'">
              {{ row.parseStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工作年限" width="100">
          <template #default="{ row }">
            {{ row.profile?.yearsOfExperience ? `${row.profile.yearsOfExperience}年` : '-' }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Match Results -->
    <div class="content-card fade-in">
      <div class="content-card-header">
        <h3 class="content-card-title">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="20" x2="18" y2="10"/>
            <line x1="12" y1="20" x2="12" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="14"/>
          </svg>
          匹配结果榜
        </h3>
        <div class="content-card-actions">
          <el-tag v-if="matches.length" type="info" size="small">
            {{ matches.length }} 位候选人
          </el-tag>
          <el-tag v-if="passedMatches.length" type="success" size="small">
            {{ passedMatches.length }} 人通过门槛
          </el-tag>
          <el-button
            type="primary"
            :loading="inviting"
            :disabled="!canBatchInvite"
            @click="onBatchInvite"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px;">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            批量邀约面试
            <span v-if="selectedInviteable.length">（{{ selectedInviteable.length }}）</span>
          </el-button>
        </div>
      </div>

      <el-table
        ref="matchTableRef"
        :key="matchTableKey"
        :data="matches"
        row-key="id"
        class="styled-table"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" :selectable="selectableRow" />
        <el-table-column label="排名" width="70">
          <template #default="{ $index }">
            <span class="rank-badge" :class="{ top: $index < 3 }">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="候选人">
          <template #default="{ row }">
            <span class="candidate-cell">
              <span class="candidate-name">{{ row.candidateName || row.fileName }}</span>
              <el-tag v-if="isInvited(row)" type="success" size="small" effect="plain">
                已邀约
              </el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="门槛" width="100">
          <template #default="{ row }">
            <el-tag :type="row.passHardGate ? 'success' : 'warning'" size="small">
              {{ row.passHardGate ? '通过' : '未过' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="综合分" width="110">
          <template #default="{ row }">
            <span class="score-value">{{ row.totalScore?.toFixed(1) ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="summary" label="摘要" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                size="small"
                link
                type="primary"
                @click="router.push(`/candidates/${row.candidateId}`)"
              >
                简历详情
              </el-button>
              <el-button
                v-if="isInvited(row)"
                size="small"
                link
                type="primary"
                @click="router.push(`/candidates/${row.candidateId}/interview`)"
              >
                面试详情
              </el-button>
              <el-button
                v-if="row.passHardGate && !isInvited(row)"
                type="success"
                size="small"
                plain
                :loading="inviting"
                @click="onInviteOne(row)"
              >
                邀约面试
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
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

/* Job Info Card */
.job-info-card {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 28px;
}

.job-info-header {
  margin-bottom: 24px;
}

.job-info-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 10px;
  letter-spacing: -0.01em;
}

.job-info-desc {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
  font-size: 14px;
}

/* Info Sections */
.info-section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border-light);
}

.info-section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 16px;
}

/* Threshold List */
.threshold-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.threshold-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.threshold-label {
  color: var(--color-text-secondary);
  font-weight: 500;
}

.threshold-value {
  color: var(--color-primary);
  font-weight: 600;
}

/* Dimension Grid */
.dimension-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px;
}

.dimension-card {
  padding: 14px 16px;
  background: var(--color-bg);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-sm);
  transition: var(--transition);
}

.dimension-card:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
}

.dimension-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.dimension-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.dimension-weight {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}

.dimension-bar-bg {
  height: 6px;
  background: var(--color-border-light);
  border-radius: 3px;
  overflow: hidden;
}

.dimension-bar {
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 3px;
  transition: width 0.4s ease-out;
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

.content-card-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* Table Styles */
.candidate-name {
  font-weight: 500;
  color: var(--color-text);
}

.candidate-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  background: var(--color-bg);
  color: var(--color-text-secondary);
}

.rank-badge.top {
  background: var(--gradient-primary);
  color: white;
}

.score-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
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
