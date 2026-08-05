<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type UploadRawFile } from 'element-plus'
import {
  fetchCandidates,
  fetchJob,
  fetchMatches,
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
let uploadingLock = false
let pendingUploadFiles: File[] = []
let uploadFlushTimer: ReturnType<typeof setTimeout> | null = null

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
</script>

<template>
  <div v-if="job" class="page-stack">
    <RouterLink class="back-link" to="/">← 返回</RouterLink>

    <el-card shadow="never" :header="job.title">
      <p class="jd-text">{{ job.jdText }}</p>

      <h4 class="block-title">硬性门槛</h4>
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item v-for="h in job.hardRequirements" :key="h.key" :label="h.label">
          {{ h.operator }} {{ h.value }}
        </el-descriptions-item>
      </el-descriptions>

      <h4 class="block-title">能力权重</h4>
      <el-row :gutter="12">
        <el-col v-for="d in job.dimensions" :key="d.name" :span="12" class="dim-col">
          <div class="panel">
            <div class="dim-row">
              <span>{{ d.name }}</span>
              <span>{{ Math.round(d.weight * 100) }}%</span>
            </div>
            <el-progress
              :percentage="Math.round(d.weight * 100)"
              :show-text="false"
              color="#1f4b7a"
            />
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>简历</span>
          <el-space>
            <el-upload
              multiple
              accept=".pdf,.docx,.txt,.md"
              :show-file-list="false"
              :before-upload="beforeUpload"
            >
              <el-button :loading="uploading" :disabled="matching">上传简历</el-button>
            </el-upload>
            <el-button type="primary" :loading="matching" :disabled="uploading" @click="onMatch">
              重新匹配
            </el-button>
          </el-space>
        </div>
      </template>

      <el-table :data="candidates" size="small" row-key="id">
        <el-table-column prop="fileName" label="文件" />
        <el-table-column label="姓名">
          <template #default="{ row }">{{ row.profile?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="parseStatus" label="解析" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.parseStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="年限" width="90">
          <template #default="{ row }">{{ row.profile?.yearsOfExperience ?? '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" header="可解释匹配结果榜">
      <el-table :data="matches" row-key="id">
        <el-table-column label="排名" width="70">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="候选人">
          <template #default="{ row }">{{ row.candidateName || row.fileName }}</template>
        </el-table-column>
        <el-table-column label="门槛" width="100">
          <template #default="{ row }">
            <el-tag :type="row.passHardGate ? 'success' : 'warning'" size="small">
              {{ row.passHardGate ? '通过' : '未过' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="综合分" width="100">
          <template #default="{ row }">
            <span class="score">{{ row.totalScore?.toFixed(1) ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="summary" label="摘要" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/candidates/${row.candidateId}`)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.back-link {
  color: #1f4b7a;
  text-decoration: none;
  font-size: 14px;
}

.jd-text {
  margin: 0 0 12px;
  color: #5b6b7c;
  line-height: 1.6;
}

.block-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dim-col {
  margin-bottom: 12px;
}

.dim-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
</style>
