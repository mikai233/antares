<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getShutdownStatus,
  startShutdown,
  type ShutdownStatusResponse,
} from '@/api/shutdown'

const loading = ref(false)
const status = ref<ShutdownStatusResponse>()
const { t } = useI18n()

const form = reactive({
  planId: '',
  requestedBy: 'gm',
})

const progressRows = computed(() => {
  const value = status.value
  if (!value) {
    return []
  }
  return [
    { name: t('Gate 排空'), done: value.drainedGateCount, expected: value.expectedGateCount },
    { name: t('Player 刷盘'), done: value.flushedPlayerCount, expected: value.expectedPlayerCount },
    { name: t('World 刷盘'), done: value.flushedWorldCount, expected: value.expectedWorldCount },
  ]
})

async function refresh() {
  loading.value = true
  try {
    status.value = await getShutdownStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('加载停服状态失败'))
  } finally {
    loading.value = false
  }
}

async function submitShutdown() {
  loading.value = true
  try {
    status.value = await startShutdown({
      planId: form.planId || null,
      requestedBy: form.requestedBy || null,
    })
    ElMessage.success(t('停服流程已启动'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('启动停服失败'))
  } finally {
    loading.value = false
  }
}

function percentage(row: { done: number; expected: number }) {
  if (row.expected <= 0) {
    return 100
  }
  return Math.min(100, Math.round((row.done / row.expected) * 100))
}

onMounted(refresh)
</script>

<template>
  <section class="stack">
      <div class="section-grid">
        <article class="panel-card metric">
          <span>{{ t('阶段') }}</span>
          <strong>{{ status?.phase ?? '-' }}</strong>
        </article>
        <article class="panel-card metric">
          <span>{{ t('计划') }}</span>
          <strong>{{ status?.planId ?? '-' }}</strong>
        </article>
        <article class="panel-card metric">
          <span>{{ t('发起人') }}</span>
          <strong>{{ status?.requestedBy ?? '-' }}</strong>
        </article>
      </div>

      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('平滑停服') }}</p>
            <h2>{{ t('停服进度') }}</h2>
          </div>
          <el-button :loading="loading" @click="refresh">{{ t('刷新') }}</el-button>
        </div>
        <el-table :data="progressRows" border>
          <el-table-column prop="name" :label="t('阶段')" min-width="160" />
          <el-table-column :label="t('进度')" min-width="220">
            <template #default="{ row }">
              <el-progress :percentage="percentage(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="t('数量')" width="130">
            <template #default="{ row }">
              {{ row.done }} / {{ row.expected }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">{{ t('错误') }}</p>
          <h2>{{ t('错误信息') }}</h2>
        </div>
        <el-empty v-if="!status?.errors.length" :description="t('暂无错误')" />
        <el-alert
          v-for="error in status?.errors ?? []"
          :key="error"
          :title="error"
          type="error"
          :closable="false"
        />
      </div>

    <div class="two-column-grid">
      <el-form class="panel-card stack" label-position="top">
        <div>
          <p class="eyebrow">{{ t('启动') }}</p>
          <h2>{{ t('启动停服') }}</h2>
        </div>
        <el-form-item :label="t('计划 ID')">
          <el-input v-model="form.planId" :placeholder="t('留空自动生成')" />
        </el-form-item>
        <el-form-item :label="t('发起人')">
          <el-input v-model="form.requestedBy" />
        </el-form-item>
        <el-button type="danger" :loading="loading" @click="submitShutdown">{{ t('启动停服') }}</el-button>
      </el-form>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">{{ t('原始数据') }}</p>
          <h2>{{ t('原始状态') }}</h2>
        </div>
        <pre class="raw-output">{{ status ? JSON.stringify(status, null, 2) : '-' }}</pre>
      </div>
    </div>
  </section>
</template>
