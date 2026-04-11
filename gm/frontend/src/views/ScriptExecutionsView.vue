<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import {
  getScriptExecution,
  listScriptExecutions,
  type ScriptExecutionResponse,
} from '@/api/script'

const loading = ref(false)
const executions = ref<ScriptExecutionResponse[]>([])
const selected = ref<ScriptExecutionResponse>()

async function refreshExecutions() {
  loading.value = true
  try {
    executions.value = await listScriptExecutions()
    if (selected.value) {
      selected.value = await getScriptExecution(selected.value.id)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载执行记录失败')
  } finally {
    loading.value = false
  }
}

async function selectExecution(row: ScriptExecutionResponse) {
  selected.value = await getScriptExecution(row.id)
}

onMounted(refreshExecutions)
</script>

<template>
  <section class="execution-layout">
    <div class="panel-card stack">
      <div class="execution-heading">
        <div>
          <p class="eyebrow">History</p>
          <h2>脚本执行记录</h2>
        </div>
        <el-button :loading="loading" @click="refreshExecutions">刷新</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="executions"
        border
        highlight-current-row
        @row-click="selectExecution"
      >
        <el-table-column prop="id" label="Execution ID" min-width="220" />
        <el-table-column prop="scriptName" label="Script" min-width="140" />
        <el-table-column prop="targetType" label="Target Type" width="140" />
        <el-table-column prop="status" label="Status" width="130" />
        <el-table-column prop="totalTargets" label="Targets" width="100" />
        <el-table-column prop="successCount" label="Success" width="100" />
        <el-table-column prop="failureCount" label="Failed" width="100" />
        <el-table-column prop="timeoutCount" label="Timeout" width="100" />
      </el-table>
    </div>

    <aside class="panel-card stack">
      <div>
        <p class="eyebrow">Detail</p>
        <h2>目标结果</h2>
      </div>

      <el-empty v-if="!selected" description="选择一条执行记录查看详情" />
      <template v-else>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Execution ID">
            <el-text tag="code">{{ selected.id }}</el-text>
          </el-descriptions-item>
          <el-descriptions-item label="Status">
            <el-tag>{{ selected.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Created">
            {{ selected.createdAt }}
          </el-descriptions-item>
        </el-descriptions>

        <el-table :data="selected.targets" border>
          <el-table-column prop="target" label="Target" min-width="140" />
          <el-table-column prop="status" label="Status" width="120" />
          <el-table-column prop="nodeAddress" label="Node" min-width="180" />
          <el-table-column prop="actorPath" label="Actor" min-width="180" />
          <el-table-column prop="error" label="Error" min-width="200" />
        </el-table>
      </template>
    </aside>
  </section>
</template>

<style scoped>
.execution-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(420px, 0.8fr);
  gap: 16px;
}

.execution-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow,
h2 {
  margin: 0;
}

.eyebrow {
  color: var(--gm-muted);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

h2 {
  margin-top: 6px;
  font-size: 30px;
  letter-spacing: -0.04em;
}

@media (max-width: 1180px) {
  .execution-layout {
    grid-template-columns: 1fr;
  }
}
</style>
