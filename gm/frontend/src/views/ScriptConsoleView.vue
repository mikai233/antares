<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { reactive, ref } from 'vue'
import {
  createScriptExecution,
  type CreateScriptExecutionRequest,
  type ScriptExecutionResponse,
} from '@/api/script'

type TargetType = CreateScriptExecutionRequest['targetType']

const loading = ref(false)
const execution = ref<ScriptExecutionResponse>()
const form = reactive({
  target: 'PlayerActor' as TargetType,
  ids: '',
  actorName: 'worker',
  actorPath: '',
  role: 'Player',
  addresses: '',
  patch: false,
  scriptFile: undefined as File | undefined,
  extraFile: undefined as File | undefined,
})

function onScriptFileChange(uploadFile: { raw?: File }) {
  form.scriptFile = uploadFile.raw
}

function onExtraFileChange(uploadFile: { raw?: File }) {
  form.extraFile = uploadFile.raw
}

function resetResults() {
  execution.value = undefined
}

function splitLines(raw: string) {
  return raw.split('\n').map(item => item.trim()).filter(Boolean)
}

function splitIds(raw: string) {
  return raw.split(',').map(item => item.trim()).filter(Boolean)
}

function createRequest(): CreateScriptExecutionRequest {
  switch (form.target) {
    case 'PlayerActor':
    case 'WorldActor':
      return {
        targetType: form.target,
        targets: splitIds(form.ids),
      }
    case 'GlobalActor':
      return {
        targetType: form.target,
        targets: [form.actorName],
      }
    case 'ActorPath':
      return {
        targetType: form.target,
        targets: [form.actorPath],
      }
    case 'Node':
      return {
        targetType: form.target,
        addresses: splitLines(form.addresses),
      }
    case 'NodeRole':
      return {
        targetType: form.target,
        role: form.role,
        addresses: splitLines(form.addresses),
        patch: form.patch,
      }
  }
}

async function submitScript() {
  if (!form.scriptFile) {
    ElMessage.warning('请选择脚本文件')
    return
  }
  loading.value = true
  resetResults()

  try {
    execution.value = await createScriptExecution({
      script: form.scriptFile,
      extra: form.extraFile,
      request: createRequest(),
    })
    ElMessage.success('脚本执行任务已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '脚本执行失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="script-layout">
    <el-form class="panel-card stack" label-position="top">
      <el-form-item label="执行目标">
        <el-segmented
          v-model="form.target"
          :options="[
            { label: '玩家 Actor', value: 'PlayerActor' },
            { label: '世界 Actor', value: 'WorldActor' },
            { label: '全局 Actor', value: 'GlobalActor' },
            { label: 'Actor Path', value: 'ActorPath' },
            { label: '节点', value: 'Node' },
            { label: '角色节点', value: 'NodeRole' },
          ]"
        />
      </el-form-item>

      <el-form-item v-if="form.target === 'PlayerActor'" label="player_id">
        <el-input v-model="form.ids" placeholder="多个 id 用逗号分隔，例如 10001,10002" />
      </el-form-item>

      <el-form-item v-if="form.target === 'WorldActor'" label="world_id">
        <el-input v-model="form.ids" placeholder="多个 id 用逗号分隔，例如 1,2" />
      </el-form-item>

      <el-form-item v-if="form.target === 'GlobalActor'" label="actor_name">
        <el-input v-model="form.actorName" placeholder="例如 worker" />
      </el-form-item>

      <el-form-item v-if="form.target === 'ActorPath'" label="actor_path">
        <el-input v-model="form.actorPath" placeholder="pekko://.../user/..." />
      </el-form-item>

      <template v-if="form.target === 'Node' || form.target === 'NodeRole'">
        <el-form-item v-if="form.target === 'NodeRole'" label="role">
          <el-select v-model="form.role">
            <el-option label="Gate" value="Gate" />
            <el-option label="Global" value="Global" />
            <el-option label="Gm" value="Gm" />
            <el-option label="Player" value="Player" />
            <el-option label="World" value="World" />
          </el-select>
        </el-form-item>
        <el-form-item label="address 过滤">
          <el-input
            v-model="form.addresses"
            type="textarea"
            :rows="4"
            placeholder='每行一个 Address JSON；留空表示不过滤'
          />
        </el-form-item>
        <el-form-item v-if="form.target === 'NodeRole'">
          <el-switch v-model="form.patch" active-text="写入 patch" />
        </el-form-item>
      </template>

      <el-form-item label="脚本文件">
        <el-upload
          drag
          action="#"
          :auto-upload="false"
          :limit="1"
          :on-change="onScriptFileChange"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖入 jar/groovy 文件，或点击选择</div>
        </el-upload>
      </el-form-item>

      <el-form-item label="extra 附件">
        <el-upload
          action="#"
          :auto-upload="false"
          :limit="1"
          :on-change="onExtraFileChange"
        >
          <el-button>选择 extra 文件</el-button>
        </el-upload>
      </el-form-item>

      <el-button type="primary" size="large" :loading="loading" @click="submitScript">
        执行脚本
      </el-button>
    </el-form>

    <aside class="panel-card stack result-panel">
      <div>
        <p class="eyebrow">Result</p>
        <h2>执行任务</h2>
      </div>

      <el-empty v-if="!execution" description="暂无执行任务" />
      <template v-else>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Execution ID">
            <el-text tag="code">{{ execution.id }}</el-text>
          </el-descriptions-item>
          <el-descriptions-item label="Status">
            <el-tag>{{ execution.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Targets">
            {{ execution.successCount }} success /
            {{ execution.failureCount }} failed /
            {{ execution.timeoutCount }} timeout /
            {{ execution.totalTargets }} total
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="execution.targets" border>
          <el-table-column prop="target" label="Target" min-width="140" />
          <el-table-column prop="status" label="Status" width="120" />
          <el-table-column prop="nodeAddress" label="Node" min-width="180" />
          <el-table-column prop="actorPath" label="Actor" min-width="180" />
          <el-table-column prop="error" label="Error" min-width="180" />
        </el-table>
      </template>
    </aside>
  </section>
</template>

<style scoped>
.script-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.8fr);
  gap: 16px;
}

.result-panel {
  align-self: start;
  min-height: 420px;
}

.eyebrow,
.result-panel h2 {
  margin: 0;
}

.eyebrow {
  color: var(--gm-muted);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.result-panel h2 {
  margin-top: 6px;
  font-size: 30px;
  letter-spacing: -0.04em;
}

@media (max-width: 1180px) {
  .script-layout {
    grid-template-columns: 1fr;
  }
}
</style>
