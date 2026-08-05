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
</script>

<template>
  <div v-if="candidate" class="page-stack">
    <RouterLink class="back-link" :to="`/jobs/${candidate.jobId}`">← 返回岗位</RouterLink>

    <el-card shadow="never" :header="candidate.profile?.name || candidate.fileName">
      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item label="学历">{{ candidate.profile?.education || '-' }}</el-descriptions-item>
        <el-descriptions-item label="年限">
          {{ candidate.profile?.yearsOfExperience ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="技能" :span="2">
          <el-space wrap>
            <el-tag v-for="s in candidate.profile?.skills || []" :key="s" size="small">{{ s }}</el-tag>
          </el-space>
        </el-descriptions-item>
        <el-descriptions-item label="摘要" :span="2">
          {{ candidate.profile?.summary || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" header="风险点">
          <div
            v-for="(item, idx) in candidate.riskFlags || []"
            :key="`${item.type}-${idx}`"
            class="list-item"
          >
            <div class="list-title">
              <el-tag :type="severityType(item.severity)" size="small">{{ item.severity }}</el-tag>
              <span>{{ item.type }}</span>
            </div>
            <p class="list-desc">{{ item.detail }}</p>
          </div>
          <el-empty
            v-if="!(candidate.riskFlags && candidate.riskFlags.length)"
            description="暂无风险点"
            :image-size="64"
          />
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>匹配拆解</span>
              <el-space v-if="report">
                <el-tag :type="report.passHardGate ? 'success' : 'warning'" size="small">
                  {{ report.passHardGate ? '过门槛' : '未过门槛' }}
                </el-tag>
                <span class="score">{{ report.totalScore?.toFixed(1) }}</span>
              </el-space>
            </div>
          </template>

          <div
            v-for="g in report?.detail?.gateChecks || []"
            :key="g.key"
            class="gate-row"
          >
            <el-tag :type="g.passed ? 'success' : 'danger'" size="small">
              {{ g.passed ? '通过' : '未过' }}
            </el-tag>
            <span>{{ g.label }}：期望 {{ g.expected }} / 实际 {{ g.actual }}</span>
          </div>

          <div
            v-for="d in report?.detail?.dimensions || []"
            :key="d.name"
            class="dim-block"
          >
            <div class="dim-row">
              <strong>{{ d.name }}（权重 {{ Math.round(d.weight * 100) }}%）</strong>
              <span>{{ d.score.toFixed(0) }} → 加权 {{ d.weightedScore.toFixed(1) }}</span>
            </div>
            <el-progress :percentage="Math.round(d.score)" color="#1f4b7a" :stroke-width="8" />
            <p class="dim-note">
              证据：{{ d.evidence }}
              <br />
              缺口：{{ d.gap }}
            </p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="结构化面试题库">
      <p class="opening">{{ report?.interviewPack?.opening }}</p>
      <div
        v-for="(q, idx) in report?.interviewPack?.questions || []"
        :key="`${q.category}-${idx}`"
        class="list-item"
      >
        <div class="list-title">
          <el-tag size="small">{{ idx + 1 }}</el-tag>
          <el-tag type="primary" size="small">{{ q.category }}</el-tag>
          <span>{{ q.question }}</span>
        </div>
        <p class="list-desc">
          意图：{{ q.intent || '-' }}{{ q.relatedRisk ? ` · 关联风险：${q.relatedRisk}` : '' }}
        </p>
      </div>

      <h4 class="block-title">收尾提示</h4>
      <ul>
        <li v-for="t in report?.interviewPack?.closingTips || []" :key="t">{{ t }}</li>
      </ul>
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-item {
  padding: 12px 0;
  border-bottom: 1px solid #eef0f3;
}

.list-item:last-child {
  border-bottom: none;
}

.list-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-weight: 500;
}

.list-desc {
  margin: 6px 0 0;
  color: #5b6b7c;
  font-size: 13px;
  line-height: 1.5;
}

.gate-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.dim-block {
  margin-bottom: 12px;
}

.dim-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
}

.dim-note {
  margin: 4px 0 0;
  color: #5b6b7c;
  font-size: 12px;
  line-height: 1.5;
}

.opening {
  margin: 0 0 12px;
  line-height: 1.6;
}

.block-title {
  margin: 16px 0 8px;
  font-size: 15px;
}
</style>
