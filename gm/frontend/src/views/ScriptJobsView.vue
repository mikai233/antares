<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  cancelScriptJob,
  cancelScriptJobItem,
  describeScriptTarget,
  downloadCsv,
  exportScriptJobResults,
  getScriptJob,
  getScriptJobItem,
  getScriptJobSummary,
  itemError,
  listScriptJobItems,
  listScriptJobs,
  retryFailedScriptJobItems,
  retryScriptJobItem,
  scriptIdValue,
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
const selectedItem = ref<ScriptJobItem>()
const itemStatusFilter = ref('')

const jobActionForm = reactive({
  cancelReason: 'gm cancel',
  exportStatus: '',
  retryError: '',
  retryLimit: 100,
  retryTimeoutMillis: 3_000,
})

const itemActionForm = reactive({
  cancelReason: 'gm cancel item',
  retryTimeoutMillis: 3_000,
})

const selectedJobId = computed(() => selectedJob.value ? scriptIdValue(selectedJob.value.id) : '')
const selectedItemId = computed(() => selectedItem.value ? scriptIdValue(selectedItem.value.id) : '')

async function refreshJobs() {
  loading.value = true
  try {
    jobs.value = (await listScriptJobs()).jobs
    if (selectedJob.value) {
      await selectJob(scriptIdValue(selectedJob.value.id))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载脚本任务失败')
  } finally {
    loading.value = false
  }
}

async function selectJob(jobOrId: ScriptJob | string) {
  const id = typeof jobOrId === 'string' ? jobOrId : scriptIdValue(jobOrId.id)
  try {
    const [job, items, summary] = await Promise.all([
      getScriptJob(id),
      listScriptJobItems(id, { status: itemStatusFilter.value }),
      getScriptJobSummary(id),
    ])
    selectedJob.value = job
    selectedItems.value = items.items
    selectedSummary.value = summary
    selectedItem.value = undefined
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载任务详情失败')
  }
}

async function reloadSelectedJob() {
  if (selectedJobId.value) {
    await selectJob(selectedJobId.value)
  }
}

async function runCancelJob() {
  if (!selectedJobId.value) {
    return
  }
  try {
    selectedJob.value = await cancelScriptJob(selectedJobId.value, jobActionForm.cancelReason)
    ElMessage.success('任务已提交取消')
    await reloadSelectedJob()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消任务失败')
  }
}

async function runRetryFailedItems() {
  if (!selectedJobId.value) {
    return
  }
  try {
    await retryFailedScriptJobItems(selectedJobId.value, {
      error: jobActionForm.retryError,
      limit: jobActionForm.retryLimit,
      timeoutMillis: jobActionForm.retryTimeoutMillis,
    })
    ElMessage.success('失败项已提交重试')
    await reloadSelectedJob()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重试失败项失败')
  }
}

async function runExportResults() {
  if (!selectedJobId.value) {
    return
  }
  try {
    const csv = await exportScriptJobResults(selectedJobId.value, jobActionForm.exportStatus)
    downloadCsv(`script-job-${selectedJobId.value}.csv`, csv)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出结果失败')
  }
}

async function selectItem(item: ScriptJobItem) {
  if (!selectedJobId.value) {
    return
  }
  try {
    selectedItem.value = await getScriptJobItem(selectedJobId.value, scriptIdValue(item.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载 item 详情失败')
  }
}

async function runCancelItem() {
  if (!selectedJobId.value || !selectedItemId.value) {
    return
  }
  try {
    selectedItem.value = await cancelScriptJobItem(selectedJobId.value, selectedItemId.value, itemActionForm.cancelReason)
    ElMessage.success('Item 已提交取消')
    await reloadSelectedJob()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消 item 失败')
  }
}

async function runRetryItem() {
  if (!selectedJobId.value || !selectedItemId.value) {
    return
  }
  try {
    selectedItem.value = await retryScriptJobItem(selectedJobId.value, selectedItemId.value, itemActionForm.retryTimeoutMillis)
    ElMessage.success('Item 已提交重试')
    await reloadSelectedJob()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重试 item 失败')
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
            <el-text tag="code">{{ selectedJobId }}</el-text>
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

        <el-form class="job-actions" label-position="top">
          <el-form-item label="Cancel Reason">
            <el-input v-model="jobActionForm.cancelReason" />
          </el-form-item>
          <el-button type="warning" @click="runCancelJob">取消任务</el-button>

          <el-divider>Export</el-divider>
          <el-form-item label="Status">
            <el-select v-model="jobActionForm.exportStatus" clearable>
              <el-option label="Pending" value="Pending" />
              <el-option label="Running" value="Running" />
              <el-option label="Completed" value="Completed" />
              <el-option label="Failed" value="Failed" />
              <el-option label="Cancelled" value="Cancelled" />
            </el-select>
          </el-form-item>
          <el-button @click="runExportResults">导出 CSV</el-button>

          <el-divider>Retry Failed</el-divider>
          <el-form-item label="Error">
            <el-input v-model="jobActionForm.retryError" clearable />
          </el-form-item>
          <el-form-item label="Limit">
            <el-input-number v-model="jobActionForm.retryLimit" :min="1" />
          </el-form-item>
          <el-form-item label="Timeout Millis">
            <el-input-number v-model="jobActionForm.retryTimeoutMillis" :min="1000" :step="1000" />
          </el-form-item>
          <el-button type="primary" @click="runRetryFailedItems">重试失败项</el-button>
        </el-form>

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

        <div class="item-toolbar">
          <el-select v-model="itemStatusFilter" clearable placeholder="Item Status" @change="reloadSelectedJob">
            <el-option label="Pending" value="Pending" />
            <el-option label="Running" value="Running" />
            <el-option label="Completed" value="Completed" />
            <el-option label="Failed" value="Failed" />
            <el-option label="Cancelled" value="Cancelled" />
          </el-select>
        </div>

        <el-table :data="selectedItems" border highlight-current-row @row-click="selectItem">
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

        <div class="panel-card stack nested-card">
          <div>
            <p class="eyebrow">Item</p>
            <h2>Item 详情</h2>
          </div>
          <el-empty v-if="!selectedItem" description="选择一个 item 查看详情" />
          <template v-else>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="Item ID">
                <el-text tag="code">{{ selectedItemId }}</el-text>
              </el-descriptions-item>
              <el-descriptions-item label="Status">{{ selectedItem.status }}</el-descriptions-item>
              <el-descriptions-item label="Target">{{ describeScriptTarget(selectedItem.target) }}</el-descriptions-item>
              <el-descriptions-item label="Attempts">{{ selectedItem.attempts.length }}</el-descriptions-item>
              <el-descriptions-item label="Error">{{ itemError(selectedItem) ?? '-' }}</el-descriptions-item>
            </el-descriptions>
            <el-form class="job-actions" label-position="top">
              <el-form-item label="Cancel Reason">
                <el-input v-model="itemActionForm.cancelReason" />
              </el-form-item>
              <el-button type="warning" @click="runCancelItem">取消 Item</el-button>
              <el-form-item label="Retry Timeout Millis">
                <el-input-number v-model="itemActionForm.retryTimeoutMillis" :min="1000" :step="1000" />
              </el-form-item>
              <el-button type="primary" @click="runRetryItem">重试 Item</el-button>
            </el-form>
          </template>
        </div>
      </template>
    </aside>
  </section>
</template>

<style scoped>
.job-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 380px);
  gap: 16px;
  min-width: 0;
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
  border-radius: var(--gm-radius);
  background: var(--gm-surface-soft);
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

.job-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.job-actions .el-divider {
  grid-column: 1 / -1;
  margin: 4px 0;
}

.item-toolbar {
  display: flex;
  justify-content: flex-end;
}

.nested-card {
  box-shadow: none;
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
  font-size: 18px;
  letter-spacing: 0;
}

@media (max-width: 1180px) {
  .job-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .job-actions {
    grid-template-columns: 1fr;
  }
}
</style>
