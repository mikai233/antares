<script setup lang="ts">
import { showError, showSuccess, showWarning } from '@/utils/feedback'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  downClusterNode,
  getClusterStatus,
  getRawClusterStatus,
  joinClusterNode,
  leaveClusterNode,
  type GmClusterNode,
  type GmClusterOperationResult,
  type GmClusterStatus,
} from '@/api/cluster'

const loading = ref(false)
const rawLoading = ref(false)
const { t } = useI18n()
const status = ref<GmClusterStatus>()
const rawStatus = ref('')
const lastOperation = ref<GmClusterOperationResult>()

const leaveForm = reactive({
  address: '',
  reason: 'gm leave',
})

const joinForm = reactive({
  nodeAddress: '',
  seedAddress: '',
  reason: 'gm join',
})

const downForm = reactive({
  address: '',
  reason: 'gm down',
  confirmed: false,
})

const nodes = computed(() => status.value?.nodes ?? [])
const roleRows = computed(() => {
  return Object.entries(status.value?.roleCounts ?? {})
    .map(([role, count]) => ({ role, count }))
    .sort((left, right) => left.role.localeCompare(right.role))
})

async function refreshStatus() {
  loading.value = true
  try {
    status.value = await getClusterStatus()
  } catch (error) {
    showError(error, t('加载集群状态失败'))
  } finally {
    loading.value = false
  }
}

async function loadRawStatus() {
  rawLoading.value = true
  try {
    rawStatus.value = await getRawClusterStatus()
  } catch (error) {
    showError(error, t('加载原始状态失败'))
  } finally {
    rawLoading.value = false
  }
}

async function runLeave() {
  if (!leaveForm.address.trim()) {
    showWarning(t('请填写必填项：{fields}', { fields: t('地址') }))
    return
  }
  await runOperation(() => leaveClusterNode(leaveForm))
}

async function runJoin() {
  if (!joinForm.nodeAddress.trim()) {
    showWarning(t('请填写必填项：{fields}', { fields: t('节点地址') }))
    return
  }
  await runOperation(() =>
    joinClusterNode({
      nodeAddress: joinForm.nodeAddress,
      seedAddress: joinForm.seedAddress || null,
      reason: joinForm.reason,
    }),
  )
}

async function runDown() {
  if (!downForm.address.trim()) {
    showWarning(t('请填写必填项：{fields}', { fields: t('地址') }))
    return
  }
  await runOperation(() => downClusterNode(downForm))
}

async function runOperation(operation: () => Promise<GmClusterOperationResult>) {
  loading.value = true
  try {
    lastOperation.value = await operation()
    showSuccess(t('集群操作已提交'))
    await refreshStatus()
  } catch (error) {
    showError(error, t('集群操作失败'))
  } finally {
    loading.value = false
  }
}

function roleText(node: GmClusterNode) {
  return node.roles.map(String).join(', ') || '-'
}

function attributeText(node: GmClusterNode, key: string) {
  return node.attributes[key] ?? '-'
}

function acceptedText(value: boolean) {
  return value ? t('已接受') : t('未接受')
}

onMounted(refreshStatus)
</script>

<template>
  <section class="stack">
      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('集群') }}</p>
            <h2>{{ t('节点状态') }}</h2>
          </div>
          <el-button :loading="loading" @click="refreshStatus">{{ t('刷新') }}</el-button>
        </div>

        <el-table v-loading="loading" :data="nodes" border>
          <el-table-column prop="address" :label="t('地址')" min-width="260" />
          <el-table-column prop="status" :label="t('状态')" width="120" />
          <el-table-column label="Role" min-width="180">
            <template #default="{ row }">
              {{ roleText(row) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('本节点')" width="90">
            <template #default="{ row }">
              {{ attributeText(row, 'self') }}
            </template>
          </el-table-column>
          <el-table-column label="UID" min-width="160">
            <template #default="{ row }">
              {{ row.nodeId ?? '-' }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('管理接口') }}</p>
            <h2>{{ t('原始状态') }}</h2>
          </div>
          <el-button :loading="rawLoading" @click="loadRawStatus">{{ t('加载') }}</el-button>
        </div>
        <pre class="raw-output">{{ rawStatus || t('暂无数据') }}</pre>
      </div>

    <div class="two-column-grid">
      <div class="panel-card stack">
        <div>
          <p class="eyebrow">Role</p>
          <h2>{{ t('角色分布') }}</h2>
        </div>
        <el-table :data="roleRows" border>
          <el-table-column prop="role" label="Role" />
          <el-table-column prop="count" :label="t('数量')" width="100" />
        </el-table>
      </div>

      <el-form class="panel-card stack" label-position="top">
        <div>
          <p class="eyebrow">{{ t('操作') }}</p>
          <h2>{{ t('节点操作') }}</h2>
        </div>

        <el-divider>{{ t('Leave 节点') }}</el-divider>
        <el-form-item :label="t('地址')" required>
          <el-input v-model="leaveForm.address" />
          <p class="field-help">{{ t('Leave 会让指定节点主动离开集群，适合计划内下线。') }}</p>
        </el-form-item>
        <el-form-item :label="t('原因')">
          <el-input v-model="leaveForm.reason" />
          <p class="field-help">{{ t('原因会进入操作记录，建议写明工单或变更背景。') }}</p>
        </el-form-item>
        <el-button :loading="loading" @click="runLeave">{{ t('提交 Leave') }}</el-button>

        <el-divider>{{ t('Join 节点') }}</el-divider>
        <el-form-item :label="t('节点地址')" required>
          <el-input v-model="joinForm.nodeAddress" />
          <p class="field-help">{{ t('节点地址是要加入集群的目标节点地址。') }}</p>
        </el-form-item>
        <el-form-item :label="t('Seed 地址')">
          <el-input v-model="joinForm.seedAddress" />
          <p class="field-help">{{ t('Seed 地址可选；留空时由后端按当前集群状态处理。') }}</p>
        </el-form-item>
        <el-form-item :label="t('原因')">
          <el-input v-model="joinForm.reason" />
        </el-form-item>
        <el-button :loading="loading" @click="runJoin">{{ t('提交 Join') }}</el-button>

        <el-divider>{{ t('Down 节点') }}</el-divider>
        <el-form-item :label="t('地址')" required>
          <el-input v-model="downForm.address" />
          <p class="field-help">{{ t('Down 会把节点标记为不可用，适合节点失联后的人工摘除。') }}</p>
        </el-form-item>
        <el-form-item :label="t('原因')">
          <el-input v-model="downForm.reason" />
          <p class="field-help">{{ t('请在确认节点无法恢复或必须摘除后再执行 Down。') }}</p>
        </el-form-item>
        <el-checkbox v-model="downForm.confirmed">{{ t('已确认风险') }}</el-checkbox>
        <el-button type="danger" :loading="loading" @click="runDown">{{ t('提交 Down') }}</el-button>
      </el-form>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">{{ t('结果') }}</p>
          <h2>{{ t('最近操作') }}</h2>
        </div>
        <el-empty v-if="!lastOperation" :description="t('暂无操作结果')" />
        <el-descriptions v-else :column="1" border>
          <el-descriptions-item :label="t('操作')">{{ lastOperation.action }}</el-descriptions-item>
          <el-descriptions-item :label="t('目标')">{{ lastOperation.targetAddress }}</el-descriptions-item>
          <el-descriptions-item :label="t('是否接受')">{{ acceptedText(lastOperation.accepted) }}</el-descriptions-item>
          <el-descriptions-item :label="t('消息')">{{ lastOperation.message }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>
  </section>
</template>
