<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchJobs, seedDemo, type Job } from '../api'

const router = useRouter()
const jobs = ref<Job[]>([])
const loading = ref(true)
const seeding = ref(false)

async function load() {
  loading.value = true
  try {
    jobs.value = await fetchJobs()
  } catch {
    ElMessage.error('无法连接后端，请先启动 apps/api')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

async function onSeed() {
  seeding.value = true
  try {
    const res = await seedDemo()
    ElMessage.success(res.message)
    await load()
    await router.push(`/jobs/${res.jobId}`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { error?: string } }; message?: string }
    ElMessage.error(err.response?.data?.error || err.message || '加载样例失败')
  } finally {
    seeding.value = false
  }
}
</script>

<template>
  <div class="page-stack">
    <div class="panel">
      <h3 class="section-title">快速演示</h3>
      <p class="section-desc">
        一键加载「Java 后端工程师」岗位 + 4 份脱敏样例简历，并自动完成解析与可解释匹配。
        未配置 OpenAI Key 时自动走 Mock，保证能演示。
      </p>
      <el-button type="primary" :loading="seeding" @click="onSeed">
        加载样例数据并开始演示
      </el-button>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>岗位列表</span>
          <el-button @click="load">刷新</el-button>
        </div>
      </template>

      <div v-loading="loading">
        <el-empty v-if="!loading && jobs.length === 0" description="暂无岗位，请先加载样例数据" />
        <div v-else class="job-list">
          <div v-for="item in jobs" :key="item.id" class="job-item">
            <div>
              <div class="job-title">{{ item.title }}</div>
              <div class="job-meta">
                维度 {{ item.dimensions?.length || 0 }} · 门槛 {{ item.hardRequirements?.length || 0 }}
              </div>
            </div>
            <el-button type="primary" link @click="router.push(`/jobs/${item.id}`)">
              进入
            </el-button>
          </div>
        </div>
      </div>
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

.section-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}

.section-desc {
  margin: 0 0 12px;
  color: #5b6b7c;
  font-size: 14px;
  line-height: 1.6;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.job-list {
  display: flex;
  flex-direction: column;
}

.job-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid #eef0f3;
}

.job-item:last-child {
  border-bottom: none;
}

.job-title {
  font-weight: 600;
  color: #1a2332;
}

.job-meta {
  margin-top: 4px;
  color: #5b6b7c;
  font-size: 13px;
}
</style>
