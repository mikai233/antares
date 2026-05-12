<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  getShutdownStatus,
  startShutdown,
  type ShutdownStatusResponse,
} from '@/api/shutdown'

const loading = ref(false)
const status = ref<ShutdownStatusResponse>()

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
    { name: 'Gate Drained', done: value.drainedGateCount, expected: value.expectedGateCount },
    { name: 'Player Flushed', done: value.flushedPlayerCount, expected: value.expectedPlayerCount },
    { name: 'World Flushed', done: value.flushedWorldCount, expected: value.expectedWorldCount },
  ]
})

async function refresh() {
  loading.value = true
  try {
    status.value = await getShutdownStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载停服状态失败')
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
    ElMessage.success('停服流程已启动')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启动停服失败')
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
  <section class="page-grid">
    <div class="stack">
      <div class="section-grid">
        <article class="panel-card metric">
          <span>Phase</span>
          <strong>{{ status?.phase ?? '-' }}</strong>
        </article>
        <article class="panel-card metric">
          <span>Plan</span>
          <strong>{{ status?.planId ?? '-' }}</strong>
        </article>
        <article class="panel-card metric">
          <span>Requester</span>
          <strong>{{ status?.requestedBy ?? '-' }}</strong>
        </article>
      </div>

      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Graceful Shutdown</p>
            <h2>停服进度</h2>
          </div>
          <el-button :loading="loading" @click="refresh">刷新</el-button>
        </div>
        <el-table :data="progressRows" border>
          <el-table-column prop="name" label="Stage" min-width="160" />
          <el-table-column label="Progress" min-width="220">
            <template #default="{ row }">
              <el-progress :percentage="percentage(row)" />
            </template>
          </el-table-column>
          <el-table-column label="Count" width="130">
            <template #default="{ row }">
              {{ row.done }} / {{ row.expected }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">Errors</p>
          <h2>错误信息</h2>
        </div>
        <el-empty v-if="!status?.errors.length" description="暂无错误" />
        <el-alert
          v-for="error in status?.errors ?? []"
          :key="error"
          :title="error"
          type="error"
          :closable="false"
        />
      </div>
    </div>

    <aside class="stack">
      <el-form class="panel-card stack" label-position="top">
        <div>
          <p class="eyebrow">Start</p>
          <h2>启动停服</h2>
        </div>
        <el-form-item label="Plan ID">
          <el-input v-model="form.planId" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="Requested By">
          <el-input v-model="form.requestedBy" />
        </el-form-item>
        <el-button type="danger" :loading="loading" @click="submitShutdown">启动停服</el-button>
      </el-form>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">Raw</p>
          <h2>原始状态</h2>
        </div>
        <pre class="raw-output">{{ status ? JSON.stringify(status, null, 2) : '-' }}</pre>
      </div>
    </aside>
  </section>
</template>
