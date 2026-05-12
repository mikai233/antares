<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
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
    ElMessage.error(error instanceof Error ? error.message : '加载集群状态失败')
  } finally {
    loading.value = false
  }
}

async function loadRawStatus() {
  rawLoading.value = true
  try {
    rawStatus.value = await getRawClusterStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载原始状态失败')
  } finally {
    rawLoading.value = false
  }
}

async function runLeave() {
  await runOperation(() => leaveClusterNode(leaveForm))
}

async function runJoin() {
  await runOperation(() =>
    joinClusterNode({
      nodeAddress: joinForm.nodeAddress,
      seedAddress: joinForm.seedAddress || null,
      reason: joinForm.reason,
    }),
  )
}

async function runDown() {
  await runOperation(() => downClusterNode(downForm))
}

async function runOperation(operation: () => Promise<GmClusterOperationResult>) {
  loading.value = true
  try {
    lastOperation.value = await operation()
    ElMessage.success('集群操作已提交')
    await refreshStatus()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '集群操作失败')
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

onMounted(refreshStatus)
</script>

<template>
  <section class="page-grid">
    <div class="stack">
      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Cluster</p>
            <h2>节点状态</h2>
          </div>
          <el-button :loading="loading" @click="refreshStatus">刷新</el-button>
        </div>

        <el-table v-loading="loading" :data="nodes" border>
          <el-table-column prop="address" label="Address" min-width="260" />
          <el-table-column prop="status" label="Status" width="120" />
          <el-table-column label="Roles" min-width="180">
            <template #default="{ row }">
              {{ roleText(row) }}
            </template>
          </el-table-column>
          <el-table-column label="Self" width="90">
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
            <p class="eyebrow">Management</p>
            <h2>原始状态</h2>
          </div>
          <el-button :loading="rawLoading" @click="loadRawStatus">加载</el-button>
        </div>
        <pre class="raw-output">{{ rawStatus || 'empty' }}</pre>
      </div>
    </div>

    <aside class="stack">
      <div class="panel-card stack">
        <div>
          <p class="eyebrow">Roles</p>
          <h2>角色分布</h2>
        </div>
        <el-table :data="roleRows" border>
          <el-table-column prop="role" label="Role" />
          <el-table-column prop="count" label="Count" width="100" />
        </el-table>
      </div>

      <el-form class="panel-card stack" label-position="top">
        <div>
          <p class="eyebrow">Actions</p>
          <h2>节点操作</h2>
        </div>

        <el-divider>Leave</el-divider>
        <el-form-item label="Address">
          <el-input v-model="leaveForm.address" />
        </el-form-item>
        <el-form-item label="Reason">
          <el-input v-model="leaveForm.reason" />
        </el-form-item>
        <el-button :loading="loading" @click="runLeave">提交 Leave</el-button>

        <el-divider>Join</el-divider>
        <el-form-item label="Node Address">
          <el-input v-model="joinForm.nodeAddress" />
        </el-form-item>
        <el-form-item label="Seed Address">
          <el-input v-model="joinForm.seedAddress" />
        </el-form-item>
        <el-form-item label="Reason">
          <el-input v-model="joinForm.reason" />
        </el-form-item>
        <el-button :loading="loading" @click="runJoin">提交 Join</el-button>

        <el-divider>Down</el-divider>
        <el-form-item label="Address">
          <el-input v-model="downForm.address" />
        </el-form-item>
        <el-form-item label="Reason">
          <el-input v-model="downForm.reason" />
        </el-form-item>
        <el-checkbox v-model="downForm.confirmed">Confirmed</el-checkbox>
        <el-button type="danger" :loading="loading" @click="runDown">提交 Down</el-button>
      </el-form>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">Result</p>
          <h2>最近操作</h2>
        </div>
        <el-empty v-if="!lastOperation" description="暂无操作结果" />
        <el-descriptions v-else :column="1" border>
          <el-descriptions-item label="Action">{{ lastOperation.action }}</el-descriptions-item>
          <el-descriptions-item label="Target">{{ lastOperation.targetAddress }}</el-descriptions-item>
          <el-descriptions-item label="Accepted">{{ lastOperation.accepted }}</el-descriptions-item>
          <el-descriptions-item label="Message">{{ lastOperation.message }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </aside>
  </section>
</template>
