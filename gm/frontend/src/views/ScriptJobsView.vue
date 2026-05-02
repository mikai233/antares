<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import {
  describeScriptTarget,
  getScriptJob,
  getScriptJobSummary,
  itemError,
  listScriptJobItems,
  listScriptJobs,
  targetTypeLabel,
  type ScriptJob,
  type ScriptJobItem,
  type ScriptJobResultSummary,
} from '@/api/script'

const loading = ref(false)
const jobs = ref<ScriptJob[]>([])
const selectedJob = ref<ScriptJob>()
const selectedItems = ref<ScriptJobItem[]>([])
const selectedSummary = ref<ScriptJobResultSummary>()

async function refreshJobs() {
  loading.value = true
  try {
    jobs.value = (await listScriptJobs()).jobs
    if (selectedJob.value) {
      await selectJob(selectedJob.value.id)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载脚本任务失败')
  } finally {
    loading.value = false
  }
}

async function selectJob(jobOrId: ScriptJob | string) {
  const id = typeof jobOrId === 'string' ? jobOrId : jobOrId.id
  try {
    const [job, items, summary] = await Promise.all([
      getScriptJob(id),
      listScriptJobItems(id),
      getScriptJobSummary(id),
    ])
    selectedJob.value = job
    selectedItems.value = items.items
    selectedSummary.value = summary
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载任务详情失败')
  }
}

function formatTime(millis: number) {
  return new Date(millis).toLocaleString()
}

onMounted(refreshJobs)
</script>

<template>
  <section class="job-layout">
    <div class="panel-card stack">
      <div class="job-heading">
        <div>
          <p class="eyebrow">History</p>
          <h2>脚本任务记录</h2>
        </div>
        <el-button :loading="loading" @click="refreshJobs">刷新</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="jobs"
        border
        highlight-current-row
        @row-click="selectJob"
      >
        <el-table-column prop="id" label="Job ID" min-width="220" />
        <el-table-column label="Script" min-width="140">
          <template #default="{ row }">
            {{ row.command.artifact.name }}
          </template>
        </el-table-column>
        <el-table-column label="Target Type" width="130">
          <template #default="{ row }">
            {{ targetTypeLabel(row.command.target) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="Status" width="130" />
        <el-table-column prop="totalItems" label="Items" width="90" />
        <el-table-column prop="completedItems" label="Done" width="90" />
        <el-table-column prop="failedItems" label="Failed" width="90" />
        <el-table-column prop="cancelledItems" label="Cancelled" width="110" />
      </el-table>
    </div>

    <aside class="panel-card stack">
      <div>
        <p class="eyebrow">Detail</p>
        <h2>任务详情</h2>
      </div>

      <el-empty v-if="!selectedJob" description="选择一条任务记录查看详情" />
      <template v-else>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Job ID">
            <el-text tag="code">{{ selectedJob.id }}</el-text>
          </el-descriptions-item>
          <el-descriptions-item label="Script">
            {{ selectedJob.command.artifact.name }} ({{ selectedJob.command.artifact.engine }})
          </el-descriptions-item>
          <el-descriptions-item label="Target">
            {{ describeScriptTarget(selectedJob.command.target) }}
          </el-descriptions-item>
          <el-descriptions-item label="Status">
            <el-tag>{{ selectedJob.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Created">
            {{ formatTime(selectedJob.createdAtMillis) }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="selectedSummary" class="summary-grid">
          <div class="summary-card">
            <span>Total</span>
            <strong>{{ selectedSummary.totalItems }}</strong>
          </div>
          <div class="summary-card">
            <span>Completed</span>
            <strong>{{ selectedSummary.completedItems }}</strong>
          </div>
          <div class="summary-card">
            <span>Failed</span>
            <strong>{{ selectedSummary.failedItems }}</strong>
          </div>
          <div class="summary-card">
            <span>Cancelled</span>
            <strong>{{ selectedSummary.cancelledItems }}</strong>
          </div>
        </div>

        <el-table :data="selectedItems" border>
          <el-table-column label="Target" min-width="180">
            <template #default="{ row }">
              {{ describeScriptTarget(row.target) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="Status" width="120" />
          <el-table-column label="Results" width="100">
            <template #default="{ row }">
              {{ row.results.length }}
            </template>
          </el-table-column>
          <el-table-column label="Error" min-width="220">
            <template #default="{ row }">
              {{ itemError(row) ?? '-' }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </aside>
  </section>
</template>

<style scoped>
.job-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(420px, 0.8fr);
  gap: 16px;
}

.job-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--gm-line);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
}

.summary-card span {
  color: var(--gm-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.summary-card strong {
  font-size: 24px;
}

.eyebrow,
h2 {
  margin: 0;
}

.eyebrow {
  color: var(--gm-muted);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

h2 {
  margin-top: 6px;
  font-size: 30px;
  letter-spacing: -0.04em;
}

@media (max-width: 1180px) {
  .job-layout {
    grid-template-columns: 1fr;
  }
}
</style>
