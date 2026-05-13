<script setup lang="ts">
import { showError, showSuccess } from '@/utils/feedback'
import { formatServerDateTime } from '@/utils/serverTime'
import type { UploadFile } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getCurrentConfigPublication,
  getClusterConfigConsistency,
  getClusterConfigStatus,
  getConfigCenterEntry,
  getConfigCenterTree,
  getConfigMetadata,
  getConfigReloadStatus,
  getConfigRow,
  getConfigTableSchema,
  listConfigPublications,
  listConfigRows,
  listConfigReloadHistory,
  listConfigTables,
  promoteConfigPublication,
  publishAndReloadConfigPublication,
  publishConfigPublication,
  reloadClusterConfig,
  reloadLocalConfig,
  revisionText,
  validateConfigPublication,
  type ClusterConfigReloadResult,
  type ClusterConfigNodeStatus,
  type ClusterConfigRevisionConsistency,
  type ConfigPublicationPublishResponse,
  type ConfigPublicationResponse,
  type ConfigPublicationValidationResponse,
  type ConfigRevision,
  type GmConfigCenterEntryResponse,
  type GmConfigCenterTreeResponse,
  type GmConfigMetadata,
  type GmConfigReloadRecord,
  type GmConfigReloadStatus,
  type GmConfigRow,
  type GmConfigRowPage,
  type GmConfigTableDescriptor,
  type GmConfigTableSummary,
} from '@/api/config'

const loading = ref(false)
const tableLoading = ref(false)
const configCenterLoading = ref(false)
const publicationLoading = ref(false)
const ConfigCenterRootPath = '/antares'
const { t } = useI18n()
const metadata = ref<GmConfigMetadata>()
const reloadStatus = ref<GmConfigReloadStatus>()
const reloadHistory = ref<GmConfigReloadRecord[]>([])
const tables = ref<GmConfigTableSummary[]>([])
const selectedTable = ref('')
const schema = ref<GmConfigTableDescriptor>()
const rows = ref<GmConfigRowPage>()
const selectedRow = ref<GmConfigRow>()
const clusterStatuses = ref<ClusterConfigNodeStatus[]>([])
const consistency = ref<ClusterConfigRevisionConsistency>()
const clusterReloadResult = ref<ClusterConfigReloadResult>()
const configCenterTree = ref<GmConfigCenterTreeResponse>()
const configCenterEntry = ref<GmConfigCenterEntryResponse>()
const publicationFile = ref<File>()
const publicationCurrent = ref<ConfigPublicationResponse | null>(null)
const publicationHistory = ref<ConfigPublicationResponse[]>([])
const publicationValidation = ref<ConfigPublicationValidationResponse>()
const publicationPublishResult = ref<ConfigPublicationPublishResponse>()

const query = reactive({
  keyword: '',
  offset: 0,
  limit: 50,
  rowId: '',
})

const configCenterForm = reactive({
  path: ConfigCenterRootPath,
})

const publicationForm = reactive({
  version: '',
  timeoutMillis: 10_000,
})

const clusterReloadForm = reactive({
  target: 'all' as 'all' | 'role' | 'nodes' | 'addresses',
  role: '',
  nodeIds: '',
  addresses: '',
  timeoutMillis: 10_000,
})

const selectedSummary = computed(() => tables.value.find(table => table.name === selectedTable.value))
const consistencyProblemCount = computed(() => {
  const current = consistency.value
  if (!current) {
    return 0
  }
  return current.statuses.filter(status => consistencyStatusType(status) !== 'success').length
})

const consistencyRows = computed(() => {
  const current = consistency.value
  if (!current) {
    return []
  }
  const expectedRevision = dominantRevision(current)
  return current.statuses.map(status => ({
    ...status,
    revisionText: configRevisionText(status.revision),
    rolesText: status.roles.join(', ') || '-',
    statusText: consistencyStatusText(status, expectedRevision),
    statusType: consistencyStatusType(status, expectedRevision),
  }))
})

const configCenterParent = computed(() => parentConfigCenterPath(configCenterTree.value?.path ?? configCenterForm.path))
const configCenterEntryName = computed(() => {
  const path = configCenterEntry.value?.path ?? configCenterTree.value?.path ?? ConfigCenterRootPath
  if (path === ConfigCenterRootPath) {
    return ConfigCenterRootPath
  }
  const segments = path.split('/').filter(Boolean)
  return segments.at(-1) ?? '/'
})

async function refreshOverview() {
  loading.value = true
  try {
    const [nextMetadata, nextStatus, nextHistory, nextTables, nextClusterStatus, nextConsistency] = await Promise.all([
      getConfigMetadata(),
      getConfigReloadStatus(),
      listConfigReloadHistory(),
      listConfigTables(),
      getClusterConfigStatus(),
      getClusterConfigConsistency(),
    ])
    metadata.value = nextMetadata
    reloadStatus.value = nextStatus
    reloadHistory.value = nextHistory
    tables.value = nextTables
    clusterStatuses.value = nextClusterStatus
    consistency.value = nextConsistency
    if (!selectedTable.value && nextTables.length > 0) {
      selectedTable.value = nextTables[0].name
    }
  } catch (error) {
    showError(error, t('加载配置状态失败'))
  } finally {
    loading.value = false
  }
}

async function reloadLocal() {
  loading.value = true
  try {
    await reloadLocalConfig()
    showSuccess(t('本节点配置已 reload'))
    await refreshOverview()
  } catch (error) {
    showError(error, t('本节点 reload 失败'))
  } finally {
    loading.value = false
  }
}

async function reloadCluster() {
  loading.value = true
  try {
    clusterReloadResult.value = await reloadClusterConfig({
      target: clusterReloadForm.target,
      role: clusterReloadForm.role || null,
      nodeIds: csv(clusterReloadForm.nodeIds),
      addresses: lines(clusterReloadForm.addresses),
      timeoutMillis: clusterReloadForm.timeoutMillis,
    })
    showSuccess(t('集群配置 reload 已提交'))
    await refreshOverview()
  } catch (error) {
    showError(error, t('集群配置 reload 失败'))
  } finally {
    loading.value = false
  }
}

async function refreshPublications() {
  publicationLoading.value = true
  try {
    const [nextCurrent, nextHistory] = await Promise.all([
      getCurrentConfigPublication(),
      listConfigPublications(),
    ])
    publicationCurrent.value = nextCurrent
    publicationHistory.value = nextHistory
  } catch (error) {
    showError(error, t('加载配置发布失败'))
  } finally {
    publicationLoading.value = false
  }
}

function onConfigBundleChange(uploadFile: UploadFile) {
  publicationFile.value = uploadFile.raw
  publicationValidation.value = undefined
  publicationPublishResult.value = undefined
}

function onConfigBundleRemove() {
  publicationFile.value = undefined
  publicationValidation.value = undefined
  publicationPublishResult.value = undefined
}

function selectedPublicationFile() {
  const file = publicationFile.value
  if (!file) {
    showError(t('请选择配置包'), t('请选择配置包'))
    return null
  }
  return file
}

async function validatePublication() {
  const file = selectedPublicationFile()
  if (!file) {
    return
  }
  publicationLoading.value = true
  try {
    publicationValidation.value = await validateConfigPublication(file, publicationForm.version || null)
    showSuccess(t('配置包已校验'))
  } catch (error) {
    showError(error, t('校验配置包失败'))
  } finally {
    publicationLoading.value = false
  }
}

async function publishPublication(reload: boolean) {
  const file = selectedPublicationFile()
  if (!file) {
    return
  }
  publicationLoading.value = true
  try {
    publicationPublishResult.value = reload
      ? await publishAndReloadConfigPublication(file, publicationForm.timeoutMillis, publicationForm.version || null)
      : await publishConfigPublication(file, publicationForm.version || null)
    if (publicationPublishResult.value.reload) {
      clusterReloadResult.value = publicationPublishResult.value.reload
    }
    showSuccess(reload ? t('配置包已发布并触发 reload') : t('配置包已发布'))
    await Promise.all([
      refreshPublications(),
      refreshOverview(),
    ])
  } catch (error) {
    showError(error, t('发布配置包失败'))
  } finally {
    publicationLoading.value = false
  }
}

async function promotePublication(row: ConfigPublicationResponse) {
  publicationLoading.value = true
  try {
    publicationCurrent.value = await promoteConfigPublication(row.revision)
    showSuccess(t('发布已切换'))
    await Promise.all([
      refreshPublications(),
      refreshOverview(),
    ])
  } catch (error) {
    showError(error, t('提升发布失败'))
  } finally {
    publicationLoading.value = false
  }
}

async function loadSelectedTable(resetOffset = false) {
  if (!selectedTable.value) {
    return
  }
  if (resetOffset) {
    query.offset = 0
    selectedRow.value = undefined
  }
  tableLoading.value = true
  try {
    const [nextSchema, nextRows] = await Promise.all([
      getConfigTableSchema(selectedTable.value),
      listConfigRows(selectedTable.value, {
        keyword: query.keyword || null,
        offset: query.offset,
        limit: query.limit,
      }),
    ])
    schema.value = nextSchema
    rows.value = nextRows
  } catch (error) {
    showError(error, t('加载配置表失败'))
  } finally {
    tableLoading.value = false
  }
}

async function loadConfigRow(id = query.rowId) {
  if (!selectedTable.value || !id.trim()) {
    return
  }
  tableLoading.value = true
  try {
    selectedRow.value = await getConfigRow(selectedTable.value, id.trim())
    query.rowId = id.trim()
  } catch (error) {
    showError(error, t('加载配置行失败'))
  } finally {
    tableLoading.value = false
  }
}

function cellText(row: GmConfigRow, field: string) {
  const value = row.values[field]
  if (value == null) {
    return '-'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function csv(raw: string) {
  return raw.split(',').map(item => item.trim()).filter(Boolean)
}

function lines(raw: string) {
  return raw.split('\n').map(item => item.trim()).filter(Boolean)
}

function jsonText(value: unknown) {
  return value == null ? '-' : JSON.stringify(value, null, 2)
}

function configRevisionText(value?: ConfigRevision | null) {
  if (!value) {
    return '-'
  }
  return value.checksum ? `${value.version} (${value.checksum.slice(0, 8)})` : value.version
}

function formatBytes(bytes?: number | null) {
  if (bytes == null) {
    return '-'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KiB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MiB`
}

async function loadConfigCenter(path = configCenterForm.path) {
  configCenterLoading.value = true
  try {
    const nextPath = normalizeConfigCenterPath(path)
    const nextTree = await getConfigCenterTree(nextPath)
    const nextEntry = await getConfigCenterEntry(nextPath)
    configCenterTree.value = nextTree
    configCenterEntry.value = nextEntry
    configCenterForm.path = nextTree.path
  } catch (error) {
    showError(error, t('加载 ConfigCenter 数据失败'))
  } finally {
    configCenterLoading.value = false
  }
}

function openConfigCenterPath(path: string) {
  configCenterForm.path = path
  void loadConfigCenter(path)
}

function entryPreview(entry?: GmConfigCenterEntryResponse | null) {
  if (!entry || !entry.exists) {
    return t('无内容')
  }
  if (entry.preview == null) {
    return t('二进制或空内容')
  }
  return entry.truncated ? `${entry.preview}\n... ${t('已截断')}` : entry.preview
}

function normalizeConfigCenterPath(path: string) {
  const trimmed = path.trim()
  if (!trimmed || trimmed === '/') {
    return ConfigCenterRootPath
  }
  const rooted = trimmed.startsWith('/') ? trimmed : `/${trimmed}`
  return rooted.replace(/\/+$/, '') || ConfigCenterRootPath
}

function parentConfigCenterPath(path: string) {
  const normalized = normalizeConfigCenterPath(path)
  if (normalized === ConfigCenterRootPath || !normalized.startsWith(`${ConfigCenterRootPath}/`)) {
    return null
  }
  const index = normalized.lastIndexOf('/')
  const parent = index <= 0 ? ConfigCenterRootPath : normalized.slice(0, index)
  return parent.length < ConfigCenterRootPath.length ? ConfigCenterRootPath : parent
}

function successText(value: boolean) {
  return value ? t('成功') : t('失败')
}

function sameRevision(left?: ConfigRevision | null, right?: ConfigRevision | null) {
  return left?.version === right?.version && (left?.checksum ?? null) === (right?.checksum ?? null)
}

function dominantRevision(current: ClusterConfigRevisionConsistency) {
  return [...current.revisionGroups]
    .sort((left, right) => right.nodes.length - left.nodes.length)[0]
    ?.revision
}

function consistencyStatusText(status: ClusterConfigNodeStatus, expectedRevision?: ConfigRevision | null) {
  if (!status.reachable) {
    return t('不可达')
  }
  if (!status.revision) {
    return t('缺少 Revision')
  }
  if (expectedRevision && !sameRevision(status.revision, expectedRevision)) {
    return t('不一致')
  }
  return t('一致')
}

function consistencyStatusType(status: ClusterConfigNodeStatus, expectedRevision?: ConfigRevision | null) {
  if (!status.reachable) {
    return 'danger'
  }
  if (!status.revision) {
    return 'warning'
  }
  if (expectedRevision && !sameRevision(status.revision, expectedRevision)) {
    return 'danger'
  }
  return 'success'
}

watch(selectedTable, () => {
  void loadSelectedTable(true)
})

onMounted(async () => {
  await refreshOverview()
  await refreshPublications()
  await loadSelectedTable()
  await loadConfigCenter()
})
</script>

<template>
  <section class="stack">
    <div class="section-grid">
      <article class="panel-card metric">
        <span>Revision</span>
        <strong>{{ revisionText(metadata?.revision) }}</strong>
      </article>
      <article class="panel-card metric">
        <span>{{ t('配置表') }}</span>
        <strong>{{ metadata?.tableCount ?? 0 }}</strong>
      </article>
      <article class="panel-card metric">
        <span>{{ t('集群节点') }}</span>
        <strong>{{ clusterStatuses.length }}</strong>
      </article>
    </div>

    <div class="panel-card stack">
      <div class="section-heading">
        <div>
          <p class="eyebrow">{{ t('发布') }}</p>
          <h2>{{ t('配置发布') }}</h2>
        </div>
        <el-space wrap>
          <el-button :loading="publicationLoading" @click="refreshPublications">{{ t('刷新') }}</el-button>
          <el-button :loading="publicationLoading" @click="validatePublication">{{ t('校验配置包') }}</el-button>
          <el-button type="primary" :loading="publicationLoading" @click="publishPublication(false)">
            {{ t('发布配置包') }}
          </el-button>
          <el-button type="warning" :loading="publicationLoading" @click="publishPublication(true)">
            {{ t('发布并 Reload') }}
          </el-button>
        </el-space>
      </div>

      <div class="publication-grid">
        <div class="stack">
          <el-form class="inline-form" label-position="top">
            <el-form-item :label="t('配置包')">
              <el-upload
                action="#"
                accept=".zip"
                :auto-upload="false"
                :limit="1"
                :on-change="onConfigBundleChange"
                :on-remove="onConfigBundleRemove"
              >
                <el-button>{{ t('选择 game-config.zip') }}</el-button>
              </el-upload>
              <p class="field-help">{{ t('配置包会先按运行时同样的校验流程加载，发布成功后会切换 ConfigCenter current 指针。') }}</p>
            </el-form-item>
            <el-form-item :label="t('版本')">
              <el-input v-model="publicationForm.version" clearable :placeholder="t('留空使用内容 checksum')" />
            </el-form-item>
            <el-form-item :label="t('Reload 超时（毫秒）')">
              <el-input-number v-model="publicationForm.timeoutMillis" :min="1000" :step="1000" />
            </el-form-item>
          </el-form>

          <el-descriptions v-if="publicationValidation" :column="2" border>
            <el-descriptions-item label="Revision">
              {{ revisionText(publicationValidation.revision) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('配置表')">
              {{ publicationValidation.tableCount }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('组件数量')">
              {{ publicationValidation.componentCount }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('总字节')">
              {{ formatBytes(publicationValidation.totalArtifactBytes) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="publicationPublishResult"
            :title="t('最近发布')"
            type="success"
            :closable="false"
            show-icon
          >
            {{ revisionText(publicationPublishResult.publication.revision) }}
          </el-alert>
        </div>

        <div class="stack">
          <div>
            <p class="eyebrow">{{ t('当前发布') }}</p>
            <h2>{{ revisionText(publicationCurrent?.revision) }}</h2>
          </div>
          <el-empty v-if="!publicationCurrent" :description="t('暂无配置发布')" :image-size="96" />
          <el-descriptions v-else :column="1" border>
            <el-descriptions-item :label="t('发布时间')">
              {{ formatServerDateTime(publicationCurrent.publishedAt) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('生成时间')">
              {{ formatServerDateTime(publicationCurrent.generatedAt) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('Artifact 数')">
              {{ publicationCurrent.artifactCount }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('总字节')">
              {{ formatBytes(publicationCurrent.totalArtifactBytes) }}
            </el-descriptions-item>
            <el-descriptions-item label="Manifest">
              {{ publicationCurrent.manifestPath }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <div class="stack">
        <div>
          <p class="eyebrow">{{ t('历史') }}</p>
          <h2>{{ t('配置发布历史') }}</h2>
        </div>
        <el-table v-loading="publicationLoading" :data="publicationHistory" border>
          <el-table-column :label="t('状态')" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.current" type="success" effect="dark">{{ t('当前') }}</el-tag>
              <el-tag v-else>{{ t('历史') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Revision" min-width="220">
            <template #default="{ row }">
              {{ revisionText(row.revision) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('发布时间')" min-width="180">
            <template #default="{ row }">
              {{ formatServerDateTime(row.publishedAt) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('Artifact 数')" width="120">
            <template #default="{ row }">
              {{ row.artifactCount }}
            </template>
          </el-table-column>
          <el-table-column :label="t('总字节')" width="120">
            <template #default="{ row }">
              {{ formatBytes(row.totalArtifactBytes) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('操作')" width="140">
            <template #default="{ row }">
              <el-button size="small" :disabled="row.current" @click="promotePublication(row)">
                {{ t('切换') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="panel-card stack">
      <div class="section-heading">
        <div>
          <p class="eyebrow">{{ t('一致性') }}</p>
          <h2>{{ t('集群一致性') }}</h2>
        </div>
        <el-tag
          :type="consistency?.consistent ? 'success' : 'danger'"
          effect="dark"
        >
          {{ consistency?.consistent ? t('一致') : t('{count} 个问题', { count: consistencyProblemCount }) }}
        </el-tag>
      </div>

      <el-alert
        v-if="consistency && !consistency.consistent"
        :title="t('集群配置修订不一致')"
        type="error"
        :closable="false"
        show-icon
      />

      <div v-if="consistency" class="consistency-summary">
        <div>
          <span>{{ t('可达节点') }}</span>
          <strong>{{ consistency.reachableNodes.length }} / {{ consistency.statuses.length }}</strong>
        </div>
        <div>
          <span>{{ t('Revision 分组') }}</span>
          <strong>{{ consistency.revisionGroups.length }}</strong>
        </div>
        <div>
          <span>{{ t('节点数') }}</span>
          <strong>{{ consistency.statuses.length }}</strong>
        </div>
      </div>

      <div v-if="consistency" class="revision-groups">
        <div
          v-for="group in consistency.revisionGroups"
          :key="configRevisionText(group.revision)"
          class="revision-group"
          :class="{ 'revision-group-problem': !consistency.consistent }"
        >
          <span>{{ configRevisionText(group.revision) }}</span>
          <strong>{{ group.nodes.length }}</strong>
        </div>
      </div>

      <el-empty v-if="!consistency" :description="t('暂无一致性结果')" />
      <el-table v-else :data="consistencyRows" border>
        <el-table-column :label="t('状态')" width="140">
          <template #default="{ row }">
            <el-tag :type="row.statusType" effect="dark">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nodeId" :label="t('节点')" min-width="140" />
        <el-table-column prop="address" :label="t('地址')" min-width="220" />
        <el-table-column prop="rolesText" label="Role" min-width="140" />
        <el-table-column prop="revisionText" label="Revision" min-width="180" />
        <el-table-column prop="message" :label="t('消息')" min-width="180" />
      </el-table>
    </div>

    <div class="panel-card stack">
      <div class="section-heading">
        <div>
          <p class="eyebrow">{{ t('配置') }}</p>
          <h2>{{ t('配置表浏览') }}</h2>
        </div>
        <el-space wrap>
          <el-button :loading="loading" @click="refreshOverview">{{ t('刷新') }}</el-button>
          <el-button type="primary" :loading="loading" @click="reloadLocal">{{ t('Reload 本节点') }}</el-button>
        </el-space>
      </div>

      <el-form class="inline-form" label-position="top">
        <el-form-item :label="t('配置表')">
          <el-select v-model="selectedTable" filterable>
            <el-option
              v-for="table in tables"
              :key="table.name"
              :label="table.name"
              :value="table.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('关键字')">
          <el-input v-model="query.keyword" clearable />
        </el-form-item>
        <el-form-item :label="t('数量上限')">
          <el-input-number v-model="query.limit" :min="1" :max="500" />
        </el-form-item>
        <el-form-item label=" ">
          <el-button :loading="tableLoading" @click="loadSelectedTable(true)">{{ t('查询') }}</el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="selectedSummary" :column="3" border>
        <el-descriptions-item :label="t('Key 类型')">{{ selectedSummary.keyType }}</el-descriptions-item>
        <el-descriptions-item :label="t('行类型')">{{ selectedSummary.rowType }}</el-descriptions-item>
        <el-descriptions-item :label="t('行数')">{{ selectedSummary.size }}</el-descriptions-item>
      </el-descriptions>

      <el-table v-loading="tableLoading" :data="rows?.rows ?? []" border @row-click="(row: GmConfigRow) => loadConfigRow(row.id)">
        <el-table-column prop="id" label="ID" min-width="140" fixed />
        <el-table-column
          v-for="field in schema?.fields ?? []"
          :key="field.name"
          :label="field.name"
          min-width="180"
        >
          <template #default="{ row }">
            {{ cellText(row, field.name) }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel-card stack">
      <div class="section-heading">
        <div>
          <p class="eyebrow">ConfigCenter</p>
          <h2>{{ t('ConfigCenter 数据') }}</h2>
        </div>
        <el-button :loading="configCenterLoading" @click="loadConfigCenter()">{{ t('刷新') }}</el-button>
      </div>

      <el-form class="inline-form" label-position="top">
        <el-form-item :label="t('浏览路径')">
          <el-input v-model="configCenterForm.path" @keyup.enter="loadConfigCenter()" />
          <p class="field-help">{{ t('浏览路径是 ConfigCenter 中的完整路径，错误路径会返回空节点或读取失败。') }}</p>
        </el-form-item>
        <el-form-item label=" ">
          <el-space wrap>
            <el-button :loading="configCenterLoading" @click="loadConfigCenter()">{{ t('查询') }}</el-button>
            <el-button @click="openConfigCenterPath(ConfigCenterRootPath)">{{ t('根路径') }}</el-button>
            <el-button
              :disabled="!configCenterParent"
              @click="configCenterParent && openConfigCenterPath(configCenterParent)"
            >
              {{ t('父级') }}
            </el-button>
          </el-space>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="configCenterEntry?.exists" :column="3" border>
        <el-descriptions-item :label="t('当前节点')">{{ configCenterEntry.path }}</el-descriptions-item>
        <el-descriptions-item :label="t('字节')">{{ configCenterEntry.size ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="Revision">{{ configCenterEntry.revision ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-else
        :title="t('当前路径没有直接数据')"
        type="info"
        :closable="false"
        show-icon
      />

      <div class="config-center-browser-grid">
        <div class="stack">
          <div>
            <p class="eyebrow">{{ t('子节点') }}</p>
            <h2>{{ configCenterTree?.path ?? ConfigCenterRootPath }}</h2>
          </div>
          <el-table v-loading="configCenterLoading" :data="configCenterTree?.children ?? []" border>
            <el-table-column prop="name" :label="t('名称')" min-width="180" />
            <el-table-column prop="size" :label="t('字节')" width="100" />
            <el-table-column prop="revision" label="Revision" width="120" />
            <el-table-column :label="t('操作')" width="110">
              <template #default="{ row }">
                <el-button size="small" @click="openConfigCenterPath(row.path)">{{ t('打开') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="stack">
          <div>
            <p class="eyebrow">{{ t('内容预览') }}</p>
            <h2>{{ configCenterEntryName }}</h2>
          </div>
          <pre class="raw-output">{{ entryPreview(configCenterEntry) }}</pre>
        </div>
      </div>
    </div>

    <div class="config-work-grid">
      <el-form class="panel-card stack" label-position="top">
        <div>
          <p class="eyebrow">{{ t('集群 Reload') }}</p>
          <h2>{{ t('集群配置') }}</h2>
        </div>
        <el-form-item :label="t('目标')">
          <el-segmented
            v-model="clusterReloadForm.target"
            :options="[
              { label: t('全部'), value: 'all' },
              { label: 'Role', value: 'role' },
              { label: t('节点'), value: 'nodes' },
              { label: t('地址'), value: 'addresses' },
            ]"
          />
          <p class="field-help">{{ t('Reload 目标决定哪些节点重新加载配置，范围越大影响面越大。') }}</p>
        </el-form-item>
        <el-form-item v-if="clusterReloadForm.target === 'role'" label="Role">
          <el-input v-model="clusterReloadForm.role" />
          <p class="field-help">{{ t('只对包含该 Role 的节点执行配置 reload。') }}</p>
        </el-form-item>
        <el-form-item v-if="clusterReloadForm.target === 'nodes'" :label="t('节点 ID')">
          <el-input v-model="clusterReloadForm.nodeIds" :placeholder="t('逗号分隔')" />
          <p class="field-help">{{ t('节点 ID 填错会导致目标节点不会收到 reload 请求。') }}</p>
        </el-form-item>
        <el-form-item v-if="clusterReloadForm.target === 'addresses'" :label="t('地址')">
          <el-input v-model="clusterReloadForm.addresses" type="textarea" :rows="4" />
          <p class="field-help">{{ t('地址按行填写，用于精确选择需要 reload 的节点。') }}</p>
        </el-form-item>
        <el-form-item :label="t('超时（毫秒）')">
          <el-input-number v-model="clusterReloadForm.timeoutMillis" :min="1000" :step="1000" />
          <p class="field-help">{{ t('超时过短可能导致慢节点被误判失败；过长会让页面等待更久。') }}</p>
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="reloadCluster">{{ t('Reload 集群') }}</el-button>
      </el-form>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">{{ t('状态') }}</p>
          <h2>{{ t('Reload 状态') }}</h2>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('当前 Revision')">
            {{ revisionText(reloadStatus?.currentRevision) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('最近成功')">
            {{ formatServerDateTime(reloadStatus?.lastSuccess?.occurredAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('最近失败')">
            {{ reloadStatus?.lastFailure?.message ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">{{ t('行详情') }}</p>
          <h2>{{ t('单行查询') }}</h2>
        </div>
        <el-form class="inline-form" label-position="top">
          <el-form-item :label="t('行 ID')">
            <el-input v-model="query.rowId" @keyup.enter="loadConfigRow()" />
            <p class="field-help">{{ t('行 ID 使用配置表的主键格式，填错会查不到行详情。') }}</p>
          </el-form-item>
          <el-form-item label=" ">
            <el-button :loading="tableLoading" @click="loadConfigRow()">{{ t('加载行') }}</el-button>
          </el-form-item>
        </el-form>
        <el-empty v-if="!selectedRow" :description="t('暂无行详情')" :image-size="96" />
        <pre v-else class="raw-output">{{ jsonText(selectedRow) }}</pre>
      </div>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">{{ t('集群结果') }}</p>
          <h2>{{ t('执行结果') }}</h2>
        </div>
        <el-empty v-if="!clusterReloadResult" :description="t('暂无结果')" :image-size="96" />
        <el-table v-else :data="clusterReloadResult.results" border>
          <el-table-column prop="address" :label="t('地址')" min-width="160" />
          <el-table-column :label="t('是否成功')" width="100">
            <template #default="{ row }">
              {{ successText(row.success) }}
            </template>
          </el-table-column>
          <el-table-column prop="message" :label="t('消息')" min-width="180" />
        </el-table>
      </div>
    </div>

    <div class="panel-card stack">
      <div>
        <p class="eyebrow">Reload</p>
        <h2>{{ t('Reload 记录') }}</h2>
      </div>
      <el-table :data="reloadHistory" border>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="status" :label="t('状态')" width="120" />
        <el-table-column :label="t('时间')" min-width="220">
          <template #default="{ row }">
            {{ formatServerDateTime(row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="message" :label="t('消息')" min-width="220" />
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.consistency-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.consistency-summary > div {
  border: 1px solid var(--gm-line);
  border-radius: var(--gm-radius);
  padding: 10px 12px;
  background: var(--gm-surface-soft);
}

.consistency-summary span,
.consistency-summary strong {
  display: block;
}

.consistency-summary span {
  color: var(--gm-muted);
  font-size: 12px;
  font-weight: 700;
}

.consistency-summary strong {
  margin-top: 6px;
  color: var(--gm-text);
  font-size: 18px;
}

.revision-groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
}

.revision-group {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  border: 1px solid var(--gm-line);
  border-radius: var(--gm-radius);
  padding: 10px 12px;
  background: var(--gm-surface-soft);
}

.revision-group span {
  min-width: 0;
  overflow: hidden;
  color: var(--gm-text);
  font-family: "JetBrains Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.revision-group strong {
  color: var(--gm-muted);
  font-size: 14px;
}

.revision-group-problem {
  border-color: var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
}

.config-work-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-items: start;
  min-width: 0;
}

.publication-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(320px, 1fr);
  gap: 14px;
  align-items: start;
  min-width: 0;
}

.config-center-browser-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.8fr);
  gap: 14px;
  align-items: start;
  min-width: 0;
}

@media (max-width: 760px) {
  .consistency-summary {
    grid-template-columns: 1fr;
  }

  .config-work-grid,
  .publication-grid,
  .config-center-browser-grid {
    grid-template-columns: 1fr;
  }
}
</style>
