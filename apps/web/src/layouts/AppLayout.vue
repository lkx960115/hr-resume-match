<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { fetchLlmStatus } from '../api'

const llm = ref<{ provider: string; hasApiKey: boolean; model: string } | null>(null)

onMounted(() => {
  fetchLlmStatus()
    .then((data) => {
      llm.value = data
    })
    .catch(() => {
      llm.value = null
    })
})
</script>

<template>
  <div class="app-shell">
    <div class="header-row">
      <div>
        <RouterLink to="/" class="brand-link">
          <h1 class="brand">简历匹配与结构化面试助手</h1>
        </RouterLink>
        <p class="brand-sub">hr-resume-match · 硬性门槛 + 能力证据 + 权重排序 · 可演示 Demo</p>
      </div>
      <el-space>
        <el-tag v-if="llm" :type="llm.provider === 'openai' ? 'primary' : 'info'">
          LLM: {{ llm.provider }} / {{ llm.model }}
        </el-tag>
        <el-tag v-else type="info">后端未连接</el-tag>
      </el-space>
    </div>
    <RouterView />
  </div>
</template>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
}

.brand-link {
  text-decoration: none;
}
</style>
