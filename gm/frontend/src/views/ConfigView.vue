<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  getClusterConfigConsistency,
  getClusterConfigStatus,
  getConfigMetadata,
  getConfigReloadStatus,
  getConfigRow,
  getConfigTableSchema,
  listConfigRows,
  listConfigReloadHistory,
  listConfigTables,
  reloadClusterConfig,
  reloadLocalConfig,
  revisionText,
  type ClusterConfigReloadResult,
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
const metadata = ref<GmConfigMetadata>()
const reloadStatus = ref<GmConfigReloadStatus>()
const reloadHistory = ref<GmConfigReloadRecord[]>([])
const tables = ref<GmConfigTableSummary[]>([])
const selectedTable = ref('')
const schema = ref<GmConfigTableDescriptor>()
const rows = ref<GmConfigRowPage>()
const selectedRow = ref<GmConfigRow>()
const clusterStatuses = ref<unknown[]>([])
const consistency = ref<unknown>()
const clusterReloadResult = ref<ClusterConfigReloadResult>()

const query = reactive({
  keyword: '',
  offset: 0,
  limit: 50,
  rowId: '',
})

const clusterReloadForm = reactive({
  target: 'all' as 'all' | 'role' | 'nodes' | 'addresses',
  role: '',
  nodeIds: '',
  addresses: '',
  timeoutMillis: 10_000,
})

const selectedSummary = computed(() => tables.value.find(table => table.name === selectedTable.value))

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
    ElMessage.error(error instanceof Error ? error.message : '加载配置状态失败')
  } finally {
    loading.value = false
  }
}

async function reloadLocal() {
  loading.value = true
  try {
    await reloadLocalConfig()
    ElMessage.success('本节点配置已 reload')
    await refreshOverview()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '本节点 reload 失败')
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
    ElMessage.success('集群配置 reload 已提交')
    await refreshOverview()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '集群配置 reload 失败')
  } finally {
    loading.value = false
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
    ElMessage.error(error instanceof Error ? error.message : '加载配置表失败')
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
    ElMessage.error(error instanceof Error ? error.message : '加载配置行失败')
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

watch(selectedTable, () => {
  void loadSelectedTable(true)
})

onMounted(async () => {
  await refreshOverview()
  await loadSelectedTable()
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
        <span>Tables</span>
        <strong>{{ metadata?.tableCount ?? 0 }}</strong>
      </article>
      <article class="panel-card metric">
        <span>Cluster Nodes</span>
        <strong>{{ clusterStatuses.length }}</strong>
      </article>
    </div>

    <section class="page-grid">
      <div class="stack">
        <div class="panel-card stack">
          <div class="section-heading">
            <div>
              <p class="eyebrow">Config</p>
              <h2>配置表浏览</h2>
            </div>
            <el-space wrap>
              <el-button :loading="loading" @click="refreshOverview">刷新</el-button>
              <el-button type="primary" :loading="loading" @click="reloadLocal">Reload Local</el-button>
            </el-space>
          </div>

          <el-form class="inline-form" label-position="top">
            <el-form-item label="Table">
              <el-select v-model="selectedTable" filterable>
                <el-option
                  v-for="table in tables"
                  :key="table.name"
                  :label="table.name"
                  :value="table.name"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Keyword">
              <el-input v-model="query.keyword" clearable />
            </el-form-item>
            <el-form-item label="Limit">
              <el-input-number v-model="query.limit" :min="1" :max="500" />
            </el-form-item>
            <el-form-item label=" ">
              <el-button :loading="tableLoading" @click="loadSelectedTable(true)">查询</el-button>
            </el-form-item>
          </el-form>

          <el-descriptions v-if="selectedSummary" :column="3" border>
            <el-descriptions-item label="Key">{{ selectedSummary.keyType }}</el-descriptions-item>
            <el-descriptions-item label="Row">{{ selectedSummary.rowType }}</el-descriptions-item>
            <el-descriptions-item label="Size">{{ selectedSummary.size }}</el-descriptions-item>
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
          <div>
            <p class="eyebrow">Reload</p>
            <h2>Reload 记录</h2>
          </div>
          <el-table :data="reloadHistory" border>
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="status" label="Status" width="120" />
            <el-table-column prop="occurredAt" label="At" min-width="220" />
            <el-table-column prop="message" label="Message" min-width="220" />
          </el-table>
        </div>
      </div>

      <aside class="stack">
        <el-form class="panel-card stack" label-position="top">
          <div>
            <p class="eyebrow">Cluster Reload</p>
            <h2>集群配置</h2>
          </div>
          <el-form-item label="Target">
            <el-segmented
              v-model="clusterReloadForm.target"
              :options="[
                { label: 'All', value: 'all' },
                { label: 'Role', value: 'role' },
                { label: 'Nodes', value: 'nodes' },
                { label: 'Addresses', value: 'addresses' },
              ]"
            />
          </el-form-item>
          <el-form-item v-if="clusterReloadForm.target === 'role'" label="Role">
            <el-input v-model="clusterReloadForm.role" />
          </el-form-item>
          <el-form-item v-if="clusterReloadForm.target === 'nodes'" label="Node IDs">
            <el-input v-model="clusterReloadForm.nodeIds" placeholder="逗号分隔" />
          </el-form-item>
          <el-form-item v-if="clusterReloadForm.target === 'addresses'" label="Addresses">
            <el-input v-model="clusterReloadForm.addresses" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item label="Timeout Millis">
            <el-input-number v-model="clusterReloadForm.timeoutMillis" :min="1000" :step="1000" />
          </el-form-item>
          <el-button type="primary" :loading="loading" @click="reloadCluster">Reload Cluster</el-button>
        </el-form>

        <div class="panel-card stack">
          <div>
            <p class="eyebrow">Status</p>
            <h2>Reload 状态</h2>
          </div>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="Current">
              {{ revisionText(reloadStatus?.currentRevision) }}
            </el-descriptions-item>
            <el-descriptions-item label="Last Success">
              {{ reloadStatus?.lastSuccess?.occurredAt ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Last Failure">
              {{ reloadStatus?.lastFailure?.message ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="panel-card stack">
          <div>
            <p class="eyebrow">Row Detail</p>
            <h2>单行查询</h2>
          </div>
          <el-form class="stack" label-position="top">
            <el-form-item label="Row ID">
              <el-input v-model="query.rowId" @keyup.enter="loadConfigRow()" />
            </el-form-item>
            <el-button :loading="tableLoading" @click="loadConfigRow()">加载行</el-button>
          </el-form>
          <el-empty v-if="!selectedRow" description="暂无行详情" />
          <pre v-else class="raw-output">{{ jsonText(selectedRow) }}</pre>
        </div>

        <div class="panel-card stack">
          <div>
            <p class="eyebrow">Consistency</p>
            <h2>一致性</h2>
          </div>
          <pre class="raw-output">{{ jsonText(consistency) }}</pre>
        </div>

        <div class="panel-card stack">
          <div>
            <p class="eyebrow">Cluster Result</p>
            <h2>执行结果</h2>
          </div>
          <el-empty v-if="!clusterReloadResult" description="暂无结果" />
          <el-table v-else :data="clusterReloadResult.results" border>
            <el-table-column prop="address" label="Address" min-width="160" />
            <el-table-column prop="success" label="Success" width="100" />
            <el-table-column prop="message" label="Message" min-width="180" />
          </el-table>
        </div>
      </aside>
    </section>
  </section>
</template>
