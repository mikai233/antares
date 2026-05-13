<script setup lang="ts">
import {
  getWorldStatuses,
  type WorldHeartbeatFilter,
  type WorldRuntimeStatus,
  type WorldStatus,
  type WorldStatusListResponse,
} from '@/api/world'
import { showError } from '@/utils/feedback'
import { formatServerDateTime } from '@/utils/serverTime'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const loading = ref(false)
const response = ref<WorldStatusListResponse>()
const filters = reactive({
  worldId: '',
  name: '',
  status: '' as WorldRuntimeStatus | '',
  configured: '' as '' | 'true' | 'false',
  heartbeat: '' as WorldHeartbeatFilter | '',
  openRange: null as string[] | null,
})
const currentPage = ref(1)
const pageSize = ref(20)
const pageSizes = [10, 20, 50, 100]
const { t } = useI18n()

const worlds = computed(() => response.value?.worlds ?? [])
const totalWorlds = computed(() => response.value?.totalWorlds ?? 0)
const filteredTotal = computed(() => response.value?.total ?? 0)
const upCount = computed(() => response.value?.upWorlds ?? 0)
const loadingCount = computed(() => response.value?.loadingWorlds ?? 0)
const downCount = computed(() => response.value?.downWorlds ?? 0)
const staleCount = computed(() => response.value?.staleWorlds ?? 0)
const totalPages = computed(() => Math.max(1, Math.ceil(filteredTotal.value / pageSize.value)))
const pageStart = computed(() => filteredTotal.value === 0 ? 0 : (currentPage.value - 1) * pageSize.value + 1)
const pageEnd = computed(() => Math.min(currentPage.value * pageSize.value, filteredTotal.value))

async function refresh() {
  loading.value = true
  try {
    response.value = await getWorldStatuses({
      page: currentPage.value,
      pageSize: pageSize.value,
      worldId: filters.worldId.trim(),
      name: filters.name.trim(),
      status: filters.status,
      configured: filters.configured === '' ? undefined : filters.configured === 'true',
      heartbeat: filters.heartbeat,
      openFrom: filters.openRange?.[0],
      openTo: filters.openRange?.[1],
    })
    clampCurrentPage()
  } catch (error) {
    showError(error, t('加载 World 状态失败'))
  } finally {
    loading.value = false
  }
}

function statusText(status?: WorldRuntimeStatus | null) {
  if (!status) {
    return '-'
  }
  const labels: Record<WorldRuntimeStatus, string> = {
    Loading: t('加载中'),
    Up: t('运行中'),
    Stopping: t('停止中'),
    Down: t('已下线'),
  }
  return labels[status] ?? status
}

function statusTagType(status: WorldRuntimeStatus) {
  if (status === 'Up') {
    return 'success'
  }
  if (status === 'Down') {
    return 'danger'
  }
  return 'warning'
}

function configText(world: WorldStatus) {
  return world.configured ? t('已配置') : t('未配置')
}

function heartbeatText(world: WorldStatus) {
  if (!world.updatedAtMillis) {
    return t('未上报')
  }
  return world.stale ? t('心跳过期') : t('心跳有效')
}

function clampCurrentPage() {
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value
  }
}

function reloadFirstPage() {
  if (currentPage.value === 1) {
    void refresh()
    return
  }
  currentPage.value = 1
}

function search() {
  reloadFirstPage()
}

function resetFilters() {
  filters.worldId = ''
  filters.name = ''
  filters.status = ''
  filters.configured = ''
  filters.heartbeat = ''
  filters.openRange = null
  reloadFirstPage()
}

watch(pageSize, reloadFirstPage)

watch(currentPage, () => {
  void refresh()
})

onMounted(refresh)
</script>

<template>
  <section class="stack">
    <section class="section-grid">
      <article class="panel-card metric">
        <span>{{ t('World 总数') }}</span>
        <strong>{{ totalWorlds }}</strong>
      </article>
      <article class="panel-card metric">
        <span>{{ t('运行中') }}</span>
        <strong>{{ upCount }}</strong>
      </article>
      <article class="panel-card metric">
        <span>{{ t('加载中') }}</span>
        <strong>{{ loadingCount }}</strong>
      </article>
      <article class="panel-card metric">
        <span>{{ t('已下线') }}</span>
        <strong>{{ downCount }}</strong>
      </article>
    </section>

    <div class="panel-card stack">
      <div class="section-heading">
        <div>
          <p class="eyebrow">World</p>
          <h2>{{ t('World 列表') }}</h2>
        </div>
        <el-button :loading="loading" @click="refresh">{{ t('刷新') }}</el-button>
      </div>

      <el-form class="inline-form world-filter-form" label-position="top">
        <el-form-item :label="t('World ID')">
          <el-input v-model="filters.worldId" clearable :placeholder="t('World ID')" @keyup.enter="search" />
        </el-form-item>
        <el-form-item :label="t('World 名称')">
          <el-input v-model="filters.name" clearable :placeholder="t('World 名称')" @keyup.enter="search" />
        </el-form-item>
        <el-form-item :label="t('状态筛选')">
          <el-select v-model="filters.status" clearable :placeholder="t('全部状态')">
            <el-option :label="t('运行中')" value="Up" />
            <el-option :label="t('加载中')" value="Loading" />
            <el-option :label="t('停止中')" value="Stopping" />
            <el-option :label="t('已下线')" value="Down" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('配置状态')">
          <el-select v-model="filters.configured" clearable :placeholder="t('全部配置')">
            <el-option :label="t('已配置')" value="true" />
            <el-option :label="t('未配置')" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('心跳状态')">
          <el-select v-model="filters.heartbeat" clearable :placeholder="t('全部心跳')">
            <el-option :label="t('心跳有效')" value="Healthy" />
            <el-option :label="t('心跳异常')" value="Unhealthy" />
          </el-select>
        </el-form-item>
        <el-form-item class="world-open-filter" :label="t('开服时间')">
          <el-date-picker
            v-model="filters.openRange"
            type="datetimerange"
            unlink-panels
            value-format="YYYY-MM-DD[T]HH:mm:ss"
            :start-placeholder="t('开始时间')"
            :end-placeholder="t('结束时间')"
            :range-separator="t('至')"
          />
        </el-form-item>
        <div class="world-filter-actions">
          <el-button type="primary" :loading="loading" @click="search">{{ t('查询') }}</el-button>
          <el-button @click="resetFilters">{{ t('重置') }}</el-button>
        </div>
        <div class="world-state-note">
          {{ t('心跳过期') }}: {{ staleCount }} / {{ totalWorlds }}
        </div>
      </el-form>

      <el-table v-loading="loading" :data="worlds" border>
        <el-table-column prop="worldId" :label="t('World ID')" width="110" />
        <el-table-column :label="t('World 名称')" min-width="150">
          <template #default="{ row }">
            {{ row.name ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('状态')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="dark">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('上报状态')" width="120">
          <template #default="{ row }">
            {{ statusText(row.reportedStatus) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('所在节点')" min-width="180">
          <template #default="{ row }">
            {{ row.nodeAddress ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('节点 ID')" min-width="140">
          <template #default="{ row }">
            {{ row.nodeId ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('心跳')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.stale || !row.updatedAtMillis ? 'danger' : 'success'" effect="plain">
              {{ heartbeatText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('更新时间')" min-width="170">
          <template #default="{ row }">
            {{ formatServerDateTime(row.updatedAtMillis) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('配置状态')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.configured ? 'success' : 'warning'" effect="plain">{{ configText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('开放时间')" min-width="170">
          <template #default="{ row }">
            {{ row.openDateTime ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('在线上限')" width="110">
          <template #default="{ row }">
            {{ row.onlineLimit ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('注册上限')" width="110">
          <template #default="{ row }">
            {{ row.registerLimit ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="message" :label="t('消息')" min-width="160" />
      </el-table>

      <div class="pagination-footer">
        <span>
          {{ t('第 {from}-{to} 条，共 {total} 条', { from: pageStart, to: pageEnd, total: filteredTotal }) }}
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="pageSizes"
          :total="filteredTotal"
          background
          layout="sizes, prev, pager, next, jumper"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.section-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.world-filter-form {
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
}

.world-open-filter {
  grid-column: span 2;
}

.world-open-filter :deep(.el-date-editor) {
  width: 100%;
}

.world-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
}

.world-state-note {
  align-self: end;
  min-height: 32px;
  color: var(--gm-muted);
  font-size: 13px;
  line-height: 32px;
}

.pagination-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  color: var(--gm-muted);
  font-size: 13px;
}

.pagination-footer .el-pagination {
  justify-content: flex-end;
  min-width: 0;
}

@media (max-width: 1180px) {
  .section-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .section-grid {
    grid-template-columns: 1fr;
  }

  .world-open-filter {
    grid-column: auto;
  }

  .pagination-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .pagination-footer .el-pagination {
    justify-content: flex-start;
  }
}
</style>
