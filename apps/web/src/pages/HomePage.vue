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
  const start = Date.now()
  try {
    jobs.value = await fetchJobs()
    const elapsed = Date.now() - start
    if (elapsed < 2000) {
      await new Promise(r => setTimeout(r, 2000 - elapsed))
    }
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
    <!-- Hero Section -->
    <div class="hero fade-in">
      <div class="hero-badge">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
        </svg>
        智能招聘助手
      </div>
      <h2 class="hero-title">简历匹配与结构化面试</h2>
      <p class="hero-subtitle">
        基于硬性门槛筛选、能力证据评分、权重排序的智能匹配系统。
        一键解析简历，生成可解释的匹配报告与结构化面试题库。
      </p>
      <div class="hero-actions">
        <el-button type="primary" size="large" :loading="seeding" @click="onSeed">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 6px;">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          加载样例数据演示
        </el-button>
        <el-button size="large" @click="load" :loading="loading">
          刷新岗位列表
        </el-button>
      </div>
    </div>

    <!-- Feature Cards -->
    <div class="feature-grid fade-in">
      <div class="feature-card">
        <div class="feature-icon blue">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 11l3 3L22 4"/>
            <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
          </svg>
        </div>
        <h4 class="feature-title">硬性门槛筛选</h4>
        <p class="feature-desc">学历、年限等硬性条件自动过滤，快速淘汰不合格候选人</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon green">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
        </div>
        <h4 class="feature-title">简历智能解析</h4>
        <p class="feature-desc">PDF/Word 简历自动解析，提取技能、经验、教育等关键信息</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon purple">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="20" x2="18" y2="10"/>
            <line x1="12" y1="20" x2="12" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="14"/>
          </svg>
        </div>
        <h4 class="feature-title">多维度评分</h4>
        <p class="feature-desc">能力维度加权评分，匹配过程透明可解释，拒绝黑盒</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon orange">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <h4 class="feature-title">结构化面试</h4>
        <p class="feature-desc">根据匹配结果自动生成面试题库，精准考察候选人能力缺口</p>
      </div>
    </div>

    <!-- Job List Section -->
    <div class="job-section fade-in">
      <div class="section-header">
        <h3 class="section-title">
          <span class="section-title-bar"></span>
          岗位列表
        </h3>
        <el-button @click="load" :loading="loading" size="small">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="margin-right: 4px;">
            <polyline points="23 4 23 10 17 10"/>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
          刷新
        </el-button>
      </div>

      <div v-loading="loading" class="job-list-wrapper">
        <el-empty v-if="!loading && jobs.length === 0" description="暂无岗位，请先加载样例数据">
          <el-button type="primary" @click="onSeed" :loading="seeding">
            一键加载样例
          </el-button>
        </el-empty>

        <div v-else class="job-grid">
          <div
            v-for="(item, idx) in jobs"
            :key="item.id"
            class="job-card"
            :style="{ animationDelay: `${idx * 0.08}s` }"
            @click="router.push(`/jobs/${item.id}`)"
          >
            <div class="job-card-header">
              <h4 class="job-card-title">{{ item.title }}</h4>
              <el-button type="primary" size="small" @click.stop="router.push(`/jobs/${item.id}`)">
                进入
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="margin-left: 2px;">
                  <line x1="5" y1="12" x2="19" y2="12"/>
                  <polyline points="12 5 19 12 12 19"/>
                </svg>
              </el-button>
            </div>
            <div class="job-card-meta">
              <span class="job-card-meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="20" x2="18" y2="10"/>
                  <line x1="12" y1="20" x2="12" y2="4"/>
                  <line x1="6" y1="20" x2="6" y2="14"/>
                </svg>
                {{ item.dimensions?.length || 0 }} 个维度
              </span>
              <span class="job-card-meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 11l3 3L22 4"/>
                  <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                </svg>
                {{ item.hardRequirements?.length || 0 }} 项门槛
              </span>
            </div>
            <div class="job-card-stats">
              <div class="job-card-stat">
                <span class="job-card-stat-value">{{ item.dimensions?.length || 0 }}</span>
                <span class="job-card-stat-label">评估维度</span>
              </div>
              <div class="job-card-stat">
                <span class="job-card-stat-value">{{ item.hardRequirements?.length || 0 }}</span>
                <span class="job-card-stat-label">硬性门槛</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-stack {
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 100%;
}

/* Job Section */
.job-section {
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
}

.job-list-wrapper {
  min-height: 100px;
}

.job-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

/* Animation for job cards */
.job-card {
  animation: fadeInUp 0.4s ease-out both;
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
