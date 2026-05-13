<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getGameTimeOverride,
  getGameTimeReloadStatus,
  updateGameTimeOverride,
  type GameTimeOverrideResponse,
  type GameTimeReloadStatusResponse,
} from '@/api/gameTime'

const loading = ref(false)
const override = ref<GameTimeOverrideResponse>()
const reloadStatus = ref<GameTimeReloadStatusResponse>()
const { t } = useI18n()

const form = reactive({
  globalOffsetMillis: 0,
  reloadEpoch: 0,
})

const currentTimeText = computed(() => {
  return override.value ? new Date(override.value.currentMillis).toLocaleString() : '-'
})

async function refresh() {
  loading.value = true
  try {
    override.value = await getGameTimeOverride()
    form.globalOffsetMillis = override.value.globalOffsetMillis
    form.reloadEpoch = override.value.epoch
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('加载游戏时间失败'))
  } finally {
    loading.value = false
  }
}

async function submitOffset() {
  loading.value = true
  try {
    override.value = await updateGameTimeOverride(form.globalOffsetMillis)
    form.reloadEpoch = override.value.epoch
    ElMessage.success(t('全局时间偏移已更新'))
    await loadReloadStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('更新时间偏移失败'))
  } finally {
    loading.value = false
  }
}

async function resetOffset() {
  form.globalOffsetMillis = 0
  await submitOffset()
}

async function loadReloadStatus() {
  if (!form.reloadEpoch) {
    return
  }
  loading.value = true
  try {
    reloadStatus.value = await getGameTimeReloadStatus(form.reloadEpoch)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('加载 Reload Ack 失败'))
  } finally {
    loading.value = false
  }
}

function durationText(millis: number) {
  const sign = millis < 0 ? '-' : ''
  const absolute = Math.abs(millis)
  const seconds = Math.floor(absolute / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  return `${sign}${hours}h ${minutes % 60}m ${seconds % 60}s`
}

function successText(value: boolean) {
  return value ? t('成功') : t('失败')
}

onMounted(refresh)
</script>

<template>
  <section class="page-grid">
    <div class="stack">
      <div class="section-grid">
        <article class="panel-card metric">
          <span>Epoch</span>
          <strong>{{ override?.epoch ?? '-' }}</strong>
        </article>
        <article class="panel-card metric">
          <span>{{ t('偏移') }}</span>
          <strong>{{ durationText(override?.globalOffsetMillis ?? 0) }}</strong>
        </article>
        <article class="panel-card metric">
          <span>{{ t('当前时间') }}</span>
          <strong>{{ currentTimeText }}</strong>
        </article>
      </div>

      <el-form class="panel-card stack" label-position="top">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('游戏时间') }}</p>
            <h2>{{ t('全局时间偏移') }}</h2>
          </div>
          <el-button :loading="loading" @click="refresh">{{ t('刷新') }}</el-button>
        </div>
        <el-form-item :label="t('全局偏移（毫秒）')">
          <el-input-number v-model="form.globalOffsetMillis" :step="60_000" />
        </el-form-item>
        <el-space wrap>
          <el-button type="primary" :loading="loading" @click="submitOffset">{{ t('更新偏移') }}</el-button>
          <el-button :loading="loading" @click="resetOffset">{{ t('重置为 0') }}</el-button>
        </el-space>
      </el-form>
    </div>

    <aside class="stack">
      <el-form class="panel-card stack" label-position="top">
        <div>
          <p class="eyebrow">Reload Ack</p>
          <h2>{{ t('节点确认') }}</h2>
        </div>
        <el-form-item label="Epoch">
          <el-input-number v-model="form.reloadEpoch" :min="0" />
        </el-form-item>
        <el-button :loading="loading" @click="loadReloadStatus">{{ t('加载确认') }}</el-button>
      </el-form>

      <div class="panel-card stack">
        <el-empty v-if="!reloadStatus" :description="t('暂无确认结果')" />
        <el-table v-else :data="reloadStatus.acks" border>
          <el-table-column prop="nodeId" :label="t('节点')" min-width="160" />
          <el-table-column prop="role" label="Role" width="110" />
          <el-table-column :label="t('是否成功')" width="100">
            <template #default="{ row }">
              {{ successText(row.success) }}
            </template>
          </el-table-column>
          <el-table-column prop="error" :label="t('错误')" min-width="180" />
        </el-table>
      </div>
    </aside>
  </section>
</template>
