<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { reactive, ref } from 'vue'
import {
  executeChannelActorScript,
  executeGlobalActorScript,
  executeNodeRoleScript,
  executeNodeScript,
  executePlayerActorScript,
  executeWorldActorScript,
  type ScriptExecutionResponse,
} from '@/api/script'

type TargetType = 'player' | 'world' | 'global' | 'channel' | 'node' | 'role'

const loading = ref(false)
const results = ref<ScriptExecutionResponse[]>([])
const form = reactive({
  target: 'player' as TargetType,
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
  results.value = []
}

async function submitScript() {
  if (!form.scriptFile) {
    ElMessage.warning('请选择脚本文件')
    return
  }
  loading.value = true
  resetResults()

  const payload = {
    script: form.scriptFile,
    extra: form.extraFile,
    ids: form.ids,
    actorName: form.actorName,
    actorPath: form.actorPath,
    role: form.role,
    addresses: form.addresses.split('\n').map(item => item.trim()).filter(Boolean),
    patch: form.patch,
  }

  try {
    switch (form.target) {
      case 'player':
        results.value = await executePlayerActorScript(payload)
        break
      case 'world':
        results.value = await executeWorldActorScript(payload)
        break
      case 'global':
        results.value = [await executeGlobalActorScript(payload)]
        break
      case 'channel':
        results.value = [await executeChannelActorScript(payload)]
        break
      case 'node':
        await executeNodeScript(payload)
        ElMessage.success('节点脚本已发送')
        break
      case 'role':
        await executeNodeRoleScript(payload)
        ElMessage.success('角色脚本已发送')
        break
    }
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
            { label: '玩家 Actor', value: 'player' },
            { label: '世界 Actor', value: 'world' },
            { label: '全局 Actor', value: 'global' },
            { label: 'Actor Path', value: 'channel' },
            { label: '节点', value: 'node' },
            { label: '角色节点', value: 'role' },
          ]"
        />
      </el-form-item>

      <el-form-item v-if="form.target === 'player'" label="player_id">
        <el-input v-model="form.ids" placeholder="多个 id 用逗号分隔，例如 10001,10002" />
      </el-form-item>

      <el-form-item v-if="form.target === 'world'" label="world_id">
        <el-input v-model="form.ids" placeholder="多个 id 用逗号分隔，例如 1,2" />
      </el-form-item>

      <el-form-item v-if="form.target === 'global'" label="actor_name">
        <el-input v-model="form.actorName" placeholder="例如 worker" />
      </el-form-item>

      <el-form-item v-if="form.target === 'channel'" label="actor_path">
        <el-input v-model="form.actorPath" placeholder="pekko://.../user/..." />
      </el-form-item>

      <template v-if="form.target === 'node' || form.target === 'role'">
        <el-form-item v-if="form.target === 'role'" label="role">
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
        <el-form-item v-if="form.target === 'role'">
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
        <h2>执行结果</h2>
      </div>

      <el-empty v-if="results.length === 0" description="暂无同步结果" />
      <el-table v-else :data="results" border>
        <el-table-column prop="uid" label="UID" min-width="220" />
        <el-table-column prop="success" label="Success" width="110" />
        <el-table-column prop="error" label="Error" min-width="180" />
      </el-table>
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
