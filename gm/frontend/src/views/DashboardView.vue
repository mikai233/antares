<script setup lang="ts">
import { backendHealth, errorMessage } from '@/api/backendHealth'
import { getClusterStatus, type GmClusterStatus } from '@/api/cluster'
import {
  getClusterConfigConsistency,
  getConfigMetadata,
  getConfigReloadStatus,
  type ClusterConfigRevisionConsistency,
  type ConfigRevision,
  type GmConfigMetadata,
  type GmConfigReloadStatus,
} from '@/api/config'
import { listPatches, type RuntimePatchDescriptor } from '@/api/patch'
import { listScriptJobs, scriptIdValue, type ScriptJobPage } from '@/api/script'
import { getShutdownStatus, type ShutdownStatusResponse } from '@/api/shutdown'
import { getWorldStatuses, type WorldStatusListResponse } from '@/api/world'
import { useGmStore } from '@/stores/gm'
import { formatServerDateTime } from '@/utils/serverTime'
import { Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const gm = useGmStore()
const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const loadedAtMillis = ref(0)
const errors = ref<string[]>([])
const clusterStatus = ref<GmClusterStatus>()
const worldStatus = ref<WorldStatusListResponse>()
const configMetadata = ref<GmConfigMetadata>()
const configConsistency = ref<ClusterConfigRevisionConsistency>()
const configReloadStatus = ref<GmConfigReloadStatus>()
const shutdownStatus = ref<ShutdownStatusResponse>()
const patches = ref<RuntimePatchDescriptor[]>([])
const scriptJobs = ref<ScriptJobPage>()

const clusterNodes = computed(() => clusterStatus.value?.nodes ?? [])
const totalNodes = computed(() => clusterNodes.value.length)
const upNodes = computed(() => clusterNodes.value.filter(node => node.status === 'Up').length)
const issueNodes = computed(() => totalNodes.value - upNodes.value)
const roleRows = computed(() => {
  return Object.entries(clusterStatus.value?.roleCounts ?? {})
    .map(([role, count]) => ({ role, count }))
    .sort((left, right) => left.role.localeCompare(right.role))
})

const configRevision = computed(() => revisionText(configMetadata.value?.revision))
const configConsistentText = computed(() => {
  if (!configConsistency.value) {
    return '-'
  }
  return configConsistency.value.consistent ? t('一致') : t('不一致')
})
const configIssueCount = computed(() => {
  const consistency = configConsistency.value
  if (!consistency) {
    return 0
  }
  const expected = consistency.revisionGroups[0]?.revision
  return consistency.statuses.filter(status => !status.reachable || !sameRevision(status.revision, expected)).length
})

const enabledPatches = computed(() => patches.value.filter(patch => patch.status === 'Enabled').length)
const disabledPatches = computed(() => patches.value.filter(patch => patch.status === 'Disabled').length)
const failedPatches = computed(() => patches.value.filter(patch => patch.status === 'Failed').length)
const runningJobs = computed(() => (scriptJobs.value?.jobs ?? []).filter(job => job.status === 'Running').length)
const failedJobs = computed(() => (scriptJobs.value?.jobs ?? []).filter(job => job.status === 'Failed').length)

const shutdownProgress = computed(() => {
  const status = shutdownStatus.value
  if (!status) {
    return '-'
  }
  const total = status.expectedGateCount + status.expectedPlayerCount + status.expectedWorldCount
  const completed = status.drainedGateCount + status.flushedPlayerCount + status.flushedWorldCount
  return total === 0 ? '0 / 0' : `${completed} / ${total}`
})

const activityRows = computed(() => {
  const rows: DashboardActivity[] = []
  const latestReload = configReloadStatus.value?.recent?.[0]
  if (latestReload) {
    rows.push({
      type: t('配置 reload'),
      title: latestReload.status,
      detail: latestReload.message || revisionText(latestReload.currentRevision),
      time: latestReload.occurredAt,
      path: '/config',
    })
  }
  for (const job of (scriptJobs.value?.jobs ?? []).slice(0, 3)) {
    rows.push({
      type: t('脚本任务'),
      title: scriptIdValue(job.id),
      detail: `${scriptJobStatusText(job.status)} · ${job.completedItems}/${job.totalItems}`,
      time: job.updatedAtMillis,
      path: '/script-jobs',
    })
  }
  if (shutdownStatus.value?.planId) {
    rows.push({
      type: t('停服控制'),
      title: shutdownStatus.value.phase,
      detail: shutdownStatus.value.planId,
      path: '/shutdown',
    })
  }
  return rows.slice(0, 5)
})

async function refresh() {
  loading.value = true
  errors.value = []
  const results = await Promise.allSettled([
    getClusterStatus(),
    getWorldStatuses({ page: 1, pageSize: 1 }),
    getConfigMetadata(),
    getClusterConfigConsistency(),
    getConfigReloadStatus(),
    getShutdownStatus(),
    listPatches(),
    listScriptJobs(),
  ])
  assignResult(results[0], value => clusterStatus.value = value, t('集群管理'))
  assignResult(results[1], value => worldStatus.value = value, t('World 状态'))
  assignResult(results[2], value => configMetadata.value = value, t('配置中心'))
  assignResult(results[3], value => configConsistency.value = value, t('配置一致性'))
  assignResult(results[4], value => configReloadStatus.value = value, t('配置 reload'))
  assignResult(results[5], value => shutdownStatus.value = value, t('停服控制'))
  assignResult(results[6], value => patches.value = value, t('运行补丁'))
  assignResult(results[7], value => scriptJobs.value = value, t('任务记录'))
  loadedAtMillis.value = Date.now()
  loading.value = false
}

function assignResult<T>(result: PromiseSettledResult<T>, apply: (value: T) => void, label: string) {
  if (result.status === 'fulfilled') {
    apply(result.value)
    return
  }
  errors.value.push(`${label}: ${errorMessage(result.reason)}`)
}

function revisionText(revision?: ConfigRevision | null) {
  if (!revision) {
    return '-'
  }
  return revision.checksum ? `${revision.version} (${revision.checksum})` : revision.version
}

function sameRevision(left?: ConfigRevision | null, right?: ConfigRevision | null) {
  if (!left || !right) {
    return false
  }
  return left.version === right.version && left.checksum === right.checksum
}

function metricTone(kind: 'good' | 'warning' | 'danger' | 'neutral') {
  return `dashboard-tone-${kind}`
}

function scriptJobStatusText(status: string) {
  const labels: Record<string, string> = {
    Running: t('运行中'),
    Completed: t('已完成'),
    Failed: t('失败'),
    Cancelled: t('已取消'),
  }
  return labels[status] ?? status
}

function formatActivityTime(value?: string | number | null) {
  if (!value) {
    return '-'
  }
  return formatServerDateTime(value)
}

function go(path: string) {
  void router.push(path)
}

onMounted(refresh)

interface DashboardActivity {
  type: string
  title: string
  detail: string
  time?: string | number | null
  path: string
}
</script>

<template>
  <section class="stack dashboard-page">
    <section class="dashboard-toolbar">
      <div>
        <p class="eyebrow">{{ t('运行总览') }}</p>
        <h2>{{ t('集群运维总览') }}</h2>
      </div>
      <div class="dashboard-toolbar-actions">
        <span>{{ t('最后刷新') }}: {{ formatServerDateTime(loadedAtMillis) }}</span>
        <el-button :icon="Refresh" :loading="loading" @click="refresh">{{ t('刷新') }}</el-button>
      </div>
    </section>

    <section class="dashboard-metric-grid">
      <article class="panel-card dashboard-metric" :class="metricTone(issueNodes === 0 ? 'good' : 'danger')" @click="go('/cluster')">
        <span>{{ t('集群健康') }}</span>
        <strong>{{ upNodes }} / {{ totalNodes }}</strong>
        <small>{{ t('异常节点') }}: {{ issueNodes }}</small>
      </article>
      <article class="panel-card dashboard-metric" :class="metricTone((worldStatus?.staleWorlds ?? 0) === 0 ? 'good' : 'warning')" @click="go('/worlds')">
        <span>{{ t('World 运行态') }}</span>
        <strong>{{ worldStatus?.upWorlds ?? 0 }} / {{ worldStatus?.totalWorlds ?? 0 }}</strong>
        <small>{{ t('心跳过期') }}: {{ worldStatus?.staleWorlds ?? 0 }}</small>
      </article>
      <article class="panel-card dashboard-metric" :class="metricTone(configConsistency?.consistent ? 'good' : 'warning')" @click="go('/config')">
        <span>{{ t('配置一致性') }}</span>
        <strong>{{ configConsistentText }}</strong>
        <small>{{ t('问题节点') }}: {{ configIssueCount }}</small>
      </article>
      <article class="panel-card dashboard-metric" :class="metricTone((shutdownStatus?.phase ?? 'idle') === 'idle' ? 'neutral' : 'warning')" @click="go('/shutdown')">
        <span>{{ t('停服状态') }}</span>
        <strong>{{ shutdownStatus?.phase ?? '-' }}</strong>
        <small>{{ t('进度') }}: {{ shutdownProgress }}</small>
      </article>
    </section>

    <section class="dashboard-content-grid">
      <div class="dashboard-column">
        <div class="panel-card stack">
          <div class="section-heading">
            <div>
              <p class="eyebrow">{{ t('集群') }}</p>
              <h2>{{ t('节点与角色') }}</h2>
            </div>
            <el-button link type="primary" @click="go('/cluster')">{{ t('查看') }}</el-button>
          </div>
          <div class="dashboard-kv">
            <span>{{ t('集群') }}</span>
            <strong>{{ gm.activeCluster }}</strong>
            <span>{{ t('GM API') }}</span>
            <strong>{{ backendHealth.status }}</strong>
            <span>{{ t('操作人') }}</span>
            <strong>{{ gm.operator }}</strong>
          </div>
          <div class="dashboard-role-list">
            <div v-for="role in roleRows" :key="role.role" class="dashboard-role-row">
              <span>{{ role.role }}</span>
              <strong>{{ role.count }}</strong>
            </div>
            <el-empty v-if="roleRows.length === 0" :description="t('暂无数据')" :image-size="72" />
          </div>
        </div>

        <div class="panel-card stack">
          <div class="section-heading">
            <div>
              <p class="eyebrow">{{ t('配置中心') }}</p>
              <h2>{{ t('配置与 reload') }}</h2>
            </div>
            <el-button link type="primary" @click="go('/config')">{{ t('查看') }}</el-button>
          </div>
          <div class="dashboard-kv">
            <span>Revision</span>
            <strong>{{ configRevision }}</strong>
            <span>{{ t('配置表') }}</span>
            <strong>{{ configMetadata?.tableCount ?? 0 }}</strong>
            <span>{{ t('最后成功') }}</span>
            <strong>{{ formatServerDateTime(configReloadStatus?.lastSuccess?.occurredAt) }}</strong>
          </div>
        </div>
      </div>

      <div class="dashboard-column">
        <div class="panel-card stack">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Runtime</p>
              <h2>{{ t('业务状态') }}</h2>
            </div>
            <el-button link type="primary" @click="go('/worlds')">{{ t('查看') }}</el-button>
          </div>
          <div class="dashboard-summary-grid">
            <div>
              <span>{{ t('World 总数') }}</span>
              <strong>{{ worldStatus?.totalWorlds ?? 0 }}</strong>
            </div>
            <div>
              <span>{{ t('运行中') }}</span>
              <strong>{{ worldStatus?.upWorlds ?? 0 }}</strong>
            </div>
            <div>
              <span>{{ t('加载中') }}</span>
              <strong>{{ worldStatus?.loadingWorlds ?? 0 }}</strong>
            </div>
            <div>
              <span>{{ t('已下线') }}</span>
              <strong>{{ worldStatus?.downWorlds ?? 0 }}</strong>
            </div>
          </div>
        </div>

        <div class="panel-card stack">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Ops</p>
              <h2>{{ t('补丁与任务') }}</h2>
            </div>
            <el-button link type="primary" @click="go('/patches')">{{ t('查看') }}</el-button>
          </div>
          <div class="dashboard-summary-grid">
            <div>
              <span>{{ t('补丁总数') }}</span>
              <strong>{{ patches.length }}</strong>
            </div>
            <div>
              <span>{{ t('已启用') }}</span>
              <strong>{{ enabledPatches }}</strong>
            </div>
            <div>
              <span>{{ t('运行任务') }}</span>
              <strong>{{ runningJobs }}</strong>
            </div>
            <div>
              <span>{{ t('失败') }}</span>
              <strong>{{ failedPatches + failedJobs }}</strong>
            </div>
          </div>
          <p class="dashboard-muted">{{ t('已禁用') }}: {{ disabledPatches }}</p>
        </div>
      </div>
    </section>

    <section class="panel-card stack">
      <div class="section-heading">
        <div>
          <p class="eyebrow">{{ t('最近事件') }}</p>
          <h2>{{ t('最近操作') }}</h2>
        </div>
        <el-tag v-if="errors.length" type="warning">{{ t('{count} 个问题', { count: errors.length }) }}</el-tag>
      </div>

      <div v-if="activityRows.length" class="dashboard-activity-list">
        <button v-for="activity in activityRows" :key="`${activity.type}-${activity.title}`" class="dashboard-activity-row" @click="go(activity.path)">
          <span>{{ activity.type }}</span>
          <strong>{{ activity.title }}</strong>
          <em>{{ activity.detail || '-' }}</em>
          <time>{{ formatActivityTime(activity.time) }}</time>
        </button>
      </div>
      <el-empty v-else :description="t('暂无操作结果')" :image-size="72" />

      <div v-if="errors.length" class="dashboard-error-list">
        <p v-for="error in errors" :key="error">{{ error }}</p>
      </div>
    </section>
  </section>
</template>
