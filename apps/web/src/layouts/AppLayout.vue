<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchLlmStatus } from '../api'

const route = useRoute()
const llm = ref<{ provider: string; hasApiKey: boolean; model: string } | null>(null)
const showBackTop = ref(false)

const SCROLL_SHOW_THRESHOLD = 280

function updateBackTop() {
  showBackTop.value = window.scrollY > SCROLL_SHOW_THRESHOLD
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

watch(
  () => route.fullPath,
  () => {
    // 路由切换后根据新页面滚动位置更新显隐
    requestAnimationFrame(updateBackTop)
  },
)

onMounted(() => {
  fetchLlmStatus()
    .then((data) => {
      llm.value = data
    })
    .catch(() => {
      llm.value = null
    })

  updateBackTop()
  window.addEventListener('scroll', updateBackTop, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', updateBackTop)
})
</script>

<template>
  <div class="app-shell">
    <nav class="navbar">
      <div class="navbar-inner">
        <RouterLink to="/" class="navbar-logo">
          <div class="navbar-logo-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
              <line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/>
              <polyline points="10 9 9 9 8 9"/>
            </svg>
          </div>
          <div>
            <div class="navbar-brand">ResumeMatch Pro</div>
            <div class="navbar-brand-sub">智能简历匹配 · 结构化面试</div>
          </div>
        </RouterLink>

        <div class="navbar-right">
          <div v-if="llm" class="llm-badge" :class="{ online: llm.hasApiKey }">
            <span class="llm-dot"></span>
            <span class="llm-label">{{ llm.provider }}</span>
            <span class="llm-model">{{ llm.model }}</span>
          </div>
          <div v-else class="llm-badge offline">
            <span class="llm-dot"></span>
            <span class="llm-label">后端未连接</span>
          </div>
        </div>
      </div>
    </nav>

    <main class="main-container">
      <RouterView />
    </main>

    <Transition name="back-top">
      <button
        v-show="showBackTop"
        type="button"
        class="back-top-btn no-print"
        aria-label="返回顶部"
        title="返回顶部"
        @click="scrollToTop"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="18 15 12 9 6 15" />
        </svg>
        <span class="back-top-label">顶部</span>
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.navbar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
}

.navbar-logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: var(--shadow-primary);
}

/* LLM Status Badge */
.llm-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 500;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  transition: var(--transition);
}

.llm-badge.online {
  background: var(--color-success-bg);
  border-color: rgba(16, 185, 129, 0.2);
  color: var(--color-success);
}

.llm-badge.offline {
  background: var(--color-danger-bg);
  border-color: rgba(239, 68, 68, 0.2);
  color: var(--color-danger);
}

.llm-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.llm-badge.online .llm-dot {
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.llm-label {
  font-weight: 600;
  text-transform: capitalize;
}

.llm-model {
  opacity: 0.7;
}

/* Back to top */
.back-top-btn {
  position: fixed;
  right: 28px;
  bottom: 36px;
  z-index: 40;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 48px;
  height: 48px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-bg-elevated);
  color: var(--color-primary);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  transition: var(--transition);
}

.back-top-btn:hover {
  color: #fff;
  background: var(--gradient-primary);
  border-color: transparent;
  box-shadow: var(--shadow-primary);
  transform: translateY(-2px);
}

.back-top-label {
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.02em;
}

.back-top-enter-active,
.back-top-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.back-top-enter-from,
.back-top-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 720px) {
  .back-top-btn {
    right: 16px;
    bottom: 20px;
    width: 44px;
    height: 44px;
  }
}

@media print {
  .back-top-btn {
    display: none !important;
  }
}
</style>
