<script setup lang="ts">
import { showError, showSuccess } from '@/utils/feedback'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getGameTimeOverride,
  getGameTimeReloadStatus,
  updateGameTimeOverride,
  type GameTimeOverrideResponse,
  type GameTimeReloadStatusResponse,
} from '@/api/gameTime'

const loading = ref(false)
const ackLoading = ref(false)
const override = ref<GameTimeOverrideResponse>()
const reloadStatus = ref<GameTimeReloadStatusResponse>()
const overrideFetchedAt = ref(Date.now())
const nowTick = ref(Date.now())
const { t } = useI18n()
let clockTimer: number | undefined
let ackTimer: number | undefined

const form = reactive({
  globalOffsetMillis: 0,
  reloadEpoch: 0,
  targetMillis: undefined as number | string | undefined,
  autoRefreshAcks: true,
})

const liveCurrentMillis = computed(() => {
  if (!override.value) {
    return undefined
  }
  return override.value.currentMillis + nowTick.value - overrideFetchedAt.value
})

const estimatedServerNowMillis = computed(() => {
  if (!override.value || liveCurrentMillis.value == null) {
    return undefined
  }
  return liveCurrentMillis.value - override.value.globalOffsetMillis
})

const currentTimeText = computed(() => {
  return liveCurrentMillis.value == null ? '-' : new Date(liveCurrentMillis.value).toLocaleString()
})

const targetTimeText = computed(() => {
  const targetMillis = targetMillisValue()
  return targetMillis == null ? '-' : new Date(targetMillis).toLocaleString()
})

const ackSuccessCount = computed(() => reloadStatus.value?.acks.filter(ack => ack.success).length ?? 0)
const ackTotalCount = computed(() => reloadStatus.value?.acks.length ?? 0)
const ackSummaryText = computed(() => `${ackSuccessCount.value} / ${ackTotalCount.value}`)
const hasAckFailures = computed(() => reloadStatus.value?.acks.some(ack => !ack.success || ack.error) ?? false)
const ackTagType = computed(() => {
  if (!reloadStatus.value || ackTotalCount.value === 0) {
    return 'info'
  }
  return hasAckFailures.value ? 'danger' : 'success'
})

async function refresh() {
  loading.value = true
  try {
    applyOverride(await getGameTimeOverride())
    if (form.autoRefreshAcks) {
      await loadReloadStatus(true)
    }
  } catch (error) {
    showError(error, t('加载游戏时间失败'))
  } finally {
    loading.value = false
  }
}

async function submitOffset() {
  loading.value = true
  try {
    applyOverride(await updateGameTimeOverride(form.globalOffsetMillis))
    showSuccess(t('全局时间偏移已更新'))
    await loadReloadStatus(true)
  } catch (error) {
    showError(error, t('更新时间偏移失败'))
  } finally {
    loading.value = false
  }
}

async function resetOffset() {
  form.globalOffsetMillis = 0
  form.targetMillis = undefined
  await submitOffset()
}

async function loadReloadStatus(silent = false) {
  if (!form.reloadEpoch) {
    return
  }
  if (!silent) {
    ackLoading.value = true
  }
  try {
    reloadStatus.value = await getGameTimeReloadStatus(form.reloadEpoch)
  } catch (error) {
    if (!silent) {
      showError(error, t('加载 Reload Ack 失败'))
    }
  } finally {
    ackLoading.value = false
  }
}

function applyOverride(nextOverride: GameTimeOverrideResponse) {
  override.value = nextOverride
  overrideFetchedAt.value = Date.now()
  nowTick.value = overrideFetchedAt.value
  form.globalOffsetMillis = nextOverride.globalOffsetMillis
  form.reloadEpoch = nextOverride.epoch
}

function applyTargetTime() {
  const targetMillis = targetMillisValue()
  if (targetMillis == null || estimatedServerNowMillis.value == null) {
    return
  }
  form.globalOffsetMillis = Math.round(targetMillis - estimatedServerNowMillis.value)
}

function setTargetFromCurrent() {
  if (liveCurrentMillis.value == null) {
    return
  }
  form.targetMillis = liveCurrentMillis.value
  applyTargetTime()
}

function shiftTarget(deltaMillis: number) {
  form.targetMillis = (targetMillisValue() ?? liveCurrentMillis.value ?? Date.now()) + deltaMillis
  applyTargetTime()
}

function targetMillisValue() {
  if (form.targetMillis == null || form.targetMillis === '') {
    return undefined
  }
  const value = Number(form.targetMillis)
  return Number.isFinite(value) ? value : undefined
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

function startTimers() {
  clockTimer = window.setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
  ackTimer = window.setInterval(() => {
    if (form.autoRefreshAcks && form.reloadEpoch && !ackLoading.value) {
      void loadReloadStatus(true)
    }
  }, 3000)
}

onMounted(() => {
  startTimers()
  void refresh()
})

onBeforeUnmount(() => {
  if (clockTimer != null) {
    window.clearInterval(clockTimer)
  }
  if (ackTimer != null) {
    window.clearInterval(ackTimer)
  }
})
</script>

<template>
  <section class="stack">
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
        <span>{{ t('当前游戏服务器时间') }}</span>
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

      <el-form-item :label="t('目标游戏时间')">
        <div class="target-time-control">
          <el-date-picker
            v-model="form.targetMillis"
            type="datetime"
            :placeholder="t('选择目标日期时间')"
            value-format="x"
            @change="applyTargetTime"
          />
          <el-button @click="setTargetFromCurrent">{{ t('使用当前') }}</el-button>
          <el-button @click="shiftTarget(3_600_000)">+1h</el-button>
          <el-button @click="shiftTarget(86_400_000)">+1d</el-button>
        </div>
        <p class="form-hint">{{ t('目标时间') }}: {{ targetTimeText }}</p>
      </el-form-item>

      <el-space wrap>
        <el-button type="primary" :loading="loading" @click="submitOffset">{{ t('更新偏移') }}</el-button>
        <el-button :loading="loading" @click="resetOffset">{{ t('重置为 0') }}</el-button>
      </el-space>
    </el-form>

    <div class="two-column-grid">
      <el-form class="panel-card stack" label-position="top">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Reload Ack</p>
            <h2>{{ t('节点确认') }}</h2>
          </div>
          <el-tag :type="ackTagType">{{ ackSummaryText }}</el-tag>
        </div>
        <el-form-item label="Epoch">
          <el-input-number v-model="form.reloadEpoch" :min="0" />
        </el-form-item>
        <el-form-item :label="t('自动刷新')">
          <el-switch v-model="form.autoRefreshAcks" />
        </el-form-item>
        <el-button :loading="ackLoading" @click="loadReloadStatus()">{{ t('刷新确认') }}</el-button>
      </el-form>

      <div class="panel-card stack">
        <el-empty v-if="!reloadStatus" :description="t('暂无确认结果')" />
        <el-table v-else v-loading="ackLoading" :data="reloadStatus.acks" border>
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
    </div>
  </section>
</template>

<style scoped>
.target-time-control {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) repeat(3, auto);
  gap: 10px;
  width: 100%;
}

.target-time-control .el-date-editor {
  width: 100%;
}

.form-hint {
  margin: 8px 0 0;
  color: var(--gm-muted);
  font-size: 12px;
}

@media (max-width: 760px) {
  .target-time-control {
    grid-template-columns: 1fr;
  }
}
</style>
