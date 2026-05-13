<script setup lang="ts">
import { showError, showSuccess } from '@/utils/feedback'
import { formatServerDateTime } from '@/utils/serverTime'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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
const { t } = useI18n()

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
    showError(error, t('加载脚本任务失败'))
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
    showError(error, t('加载任务详情失败'))
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
    showSuccess(t('任务已提交取消'))
    await reloadSelectedJob()
  } catch (error) {
    showError(error, t('取消任务失败'))
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
    showSuccess(t('失败项已提交重试'))
    await reloadSelectedJob()
  } catch (error) {
    showError(error, t('重试失败项失败'))
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
    showError(error, t('导出结果失败'))
  }
}

async function selectItem(item: ScriptJobItem) {
  if (!selectedJobId.value) {
    return
  }
  try {
    selectedItem.value = await getScriptJobItem(selectedJobId.value, scriptIdValue(item.id))
  } catch (error) {
    showError(error, t('加载任务项详情失败'))
  }
}

async function runCancelItem() {
  if (!selectedJobId.value || !selectedItemId.value) {
    return
  }
  try {
    selectedItem.value = await cancelScriptJobItem(selectedJobId.value, selectedItemId.value, itemActionForm.cancelReason)
    showSuccess(t('任务项已提交取消'))
    await reloadSelectedJob()
  } catch (error) {
    showError(error, t('取消任务项失败'))
  }
}

async function runRetryItem() {
  if (!selectedJobId.value || !selectedItemId.value) {
    return
  }
  try {
    selectedItem.value = await retryScriptJobItem(selectedJobId.value, selectedItemId.value, itemActionForm.retryTimeoutMillis)
    showSuccess(t('任务项已提交重试'))
    await reloadSelectedJob()
  } catch (error) {
    showError(error, t('重试任务项失败'))
  }
}

function scriptJobStatusText(status: string) {
  const labels: Record<string, string> = {
    Pending: t('等待中'),
    Running: t('执行中'),
    Completed: t('已完成'),
    Failed: t('失败'),
    Cancelled: t('已取消'),
  }
  return labels[status] ?? status
}

onMounted(refreshJobs)
</script>

<template>
  <section class="stack">
    <div class="panel-card stack">
      <div class="job-heading">
        <div>
          <p class="eyebrow">{{ t('历史') }}</p>
          <h2>{{ t('脚本任务记录') }}</h2>
        </div>
        <el-button :loading="loading" @click="refreshJobs">{{ t('刷新') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="jobs"
        border
        highlight-current-row
        @row-click="selectJob"
      >
        <el-table-column prop="id" :label="t('任务 ID')" min-width="220" />
        <el-table-column :label="t('脚本')" min-width="140">
          <template #default="{ row }">
            {{ row.command.artifact.name }}
          </template>
        </el-table-column>
        <el-table-column :label="t('目标类型')" width="130">
          <template #default="{ row }">
            {{ targetTypeLabel(row.command.target) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('状态')" width="130">
          <template #default="{ row }">
            {{ scriptJobStatusText(row.status) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalItems" :label="t('任务项')" width="90" />
        <el-table-column prop="completedItems" :label="t('完成')" width="90" />
        <el-table-column prop="failedItems" :label="t('失败')" width="90" />
        <el-table-column prop="cancelledItems" :label="t('取消')" width="110" />
      </el-table>
    </div>

    <div class="panel-card stack">
      <div>
        <p class="eyebrow">{{ t('详情') }}</p>
        <h2>{{ t('任务详情') }}</h2>
      </div>

      <el-empty v-if="!selectedJob" :description="t('选择一条任务记录查看详情')" />
      <template v-else>
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('任务 ID')">
            <el-text tag="code">{{ selectedJobId }}</el-text>
          </el-descriptions-item>
          <el-descriptions-item :label="t('脚本')">
            {{ selectedJob.command.artifact.name }} ({{ selectedJob.command.artifact.engine }})
          </el-descriptions-item>
          <el-descriptions-item :label="t('目标')">
            {{ describeScriptTarget(selectedJob.command.target) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('状态')">
            <el-tag>{{ scriptJobStatusText(selectedJob.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('创建时间')">
            {{ formatServerDateTime(selectedJob.createdAtMillis) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form class="job-actions" label-position="top">
          <el-form-item :label="t('取消原因')">
            <el-input v-model="jobActionForm.cancelReason" />
          </el-form-item>
          <el-button type="warning" @click="runCancelJob">{{ t('取消任务') }}</el-button>

          <el-divider>{{ t('导出') }}</el-divider>
          <el-form-item :label="t('状态')">
            <el-select v-model="jobActionForm.exportStatus" clearable>
              <el-option :label="t('等待中')" value="Pending" />
              <el-option :label="t('执行中')" value="Running" />
              <el-option :label="t('已完成')" value="Completed" />
              <el-option :label="t('失败')" value="Failed" />
              <el-option :label="t('已取消')" value="Cancelled" />
            </el-select>
          </el-form-item>
          <el-button @click="runExportResults">{{ t('导出 CSV') }}</el-button>

          <el-divider>{{ t('重试失败项') }}</el-divider>
          <el-form-item :label="t('错误')">
            <el-input v-model="jobActionForm.retryError" clearable />
          </el-form-item>
          <el-form-item :label="t('数量上限')">
            <el-input-number v-model="jobActionForm.retryLimit" :min="1" />
          </el-form-item>
          <el-form-item :label="t('超时（毫秒）')">
            <el-input-number v-model="jobActionForm.retryTimeoutMillis" :min="1000" :step="1000" />
          </el-form-item>
          <el-button type="primary" @click="runRetryFailedItems">{{ t('重试失败项') }}</el-button>
        </el-form>

        <div v-if="selectedSummary" class="summary-grid">
          <div class="summary-card">
            <span>{{ t('总数') }}</span>
            <strong>{{ selectedSummary.totalItems }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('已完成') }}</span>
            <strong>{{ selectedSummary.completedItems }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('失败') }}</span>
            <strong>{{ selectedSummary.failedItems }}</strong>
          </div>
          <div class="summary-card">
            <span>{{ t('已取消') }}</span>
            <strong>{{ selectedSummary.cancelledItems }}</strong>
          </div>
        </div>

        <div class="item-toolbar">
          <el-select v-model="itemStatusFilter" clearable :placeholder="t('任务项状态')" @change="reloadSelectedJob">
            <el-option :label="t('等待中')" value="Pending" />
            <el-option :label="t('执行中')" value="Running" />
            <el-option :label="t('已完成')" value="Completed" />
            <el-option :label="t('失败')" value="Failed" />
            <el-option :label="t('已取消')" value="Cancelled" />
          </el-select>
        </div>

        <el-table :data="selectedItems" border highlight-current-row @row-click="selectItem">
          <el-table-column :label="t('目标')" min-width="180">
            <template #default="{ row }">
              {{ describeScriptTarget(row.target) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('状态')" width="120">
            <template #default="{ row }">
              {{ scriptJobStatusText(row.status) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('结果数')" width="100">
            <template #default="{ row }">
              {{ row.results?.length ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column :label="t('错误')" min-width="220">
            <template #default="{ row }">
              {{ itemError(row) ?? '-' }}
            </template>
          </el-table-column>
        </el-table>

        <div class="panel-card stack nested-card">
          <div>
            <p class="eyebrow">{{ t('任务项') }}</p>
            <h2>{{ t('任务项详情') }}</h2>
          </div>
          <el-empty v-if="!selectedItem" :description="t('选择一个任务项查看详情')" />
          <template v-else>
            <el-descriptions :column="1" border>
              <el-descriptions-item :label="t('任务项 ID')">
                <el-text tag="code">{{ selectedItemId }}</el-text>
              </el-descriptions-item>
              <el-descriptions-item :label="t('状态')">{{ scriptJobStatusText(selectedItem.status) }}</el-descriptions-item>
              <el-descriptions-item :label="t('目标')">{{ describeScriptTarget(selectedItem.target) }}</el-descriptions-item>
              <el-descriptions-item :label="t('尝试次数')">{{ selectedItem.attempts?.length ?? 0 }}</el-descriptions-item>
              <el-descriptions-item :label="t('错误')">{{ itemError(selectedItem) ?? '-' }}</el-descriptions-item>
            </el-descriptions>
            <el-form class="job-actions" label-position="top">
              <el-form-item :label="t('取消原因')">
                <el-input v-model="itemActionForm.cancelReason" />
              </el-form-item>
              <el-button type="warning" @click="runCancelItem">{{ t('取消任务项') }}</el-button>
              <el-form-item :label="t('重试超时（毫秒）')">
                <el-input-number v-model="itemActionForm.retryTimeoutMillis" :min="1000" :step="1000" />
              </el-form-item>
              <el-button type="primary" @click="runRetryItem">{{ t('重试任务项') }}</el-button>
            </el-form>
          </template>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
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

@media (max-width: 760px) {
  .job-actions {
    grid-template-columns: 1fr;
  }
}
</style>
