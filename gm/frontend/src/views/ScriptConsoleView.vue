<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createScriptJob,
  describeScriptTarget,
  getScriptMetadata,
  itemError,
  listScriptJobItems,
  scriptIdValue,
  type GmScriptMetadata,
  type GmScriptTargetRequest,
  type GmScriptTargetType,
  type ScriptJob,
  type ScriptJobItem,
} from '@/api/script'

const loading = ref(false)
const metadata = ref<GmScriptMetadata>()
const job = ref<ScriptJob>()
const items = ref<ScriptJobItem[]>([])

const form = reactive({
  target: 'entity' as GmScriptTargetType,
  entityKind: 'PlayerActor',
  ids: '',
  actorName: 'worker',
  actorPaths: '',
  role: 'Player',
  addresses: '',
  maxConcurrentItems: undefined as number | undefined,
  scriptFile: undefined as File | undefined,
  extraFile: undefined as File | undefined,
})

const roleOptions = computed(() => metadata.value?.roles ?? ['Gate', 'Global', 'Gm', 'Player', 'World'])
const entityKindOptions = computed(() => metadata.value?.entityKinds ?? ['PlayerActor', 'WorldActor'])
const singletonOptions = computed(() => metadata.value?.singletons ?? ['worker'])
const nodeAddressOptions = computed(() => metadata.value?.nodeAddresses ?? [])

function onScriptFileChange(uploadFile: { raw?: File }) {
  form.scriptFile = uploadFile.raw
}

function onExtraFileChange(uploadFile: { raw?: File }) {
  form.extraFile = uploadFile.raw
}

function splitLines(raw: string) {
  return raw.split('\n').map(item => item.trim()).filter(Boolean)
}

function splitCsv(raw: string) {
  return raw.split(',').map(item => item.trim()).filter(Boolean)
}

function createTarget(): GmScriptTargetRequest {
  switch (form.target) {
    case 'all-nodes':
      return { type: 'all-nodes' }
    case 'role':
      return {
        type: 'role',
        role: form.role,
      }
    case 'nodes':
      return {
        type: 'nodes',
        addresses: splitLines(form.addresses),
      }
    case 'actor-paths':
      return {
        type: 'actor-paths',
        paths: splitLines(form.actorPaths),
      }
    case 'entity':
      return {
        type: 'entity',
        kind: form.entityKind,
        ids: splitCsv(form.ids),
      }
    case 'singleton':
      return {
        type: 'singleton',
        name: form.actorName,
      }
  }
}

async function loadMetadata() {
  try {
    metadata.value = await getScriptMetadata()
    if (metadata.value.roles.length > 0 && !metadata.value.roles.includes(form.role)) {
      form.role = metadata.value.roles[0]
    }
    if (metadata.value.entityKinds.length > 0 && !metadata.value.entityKinds.includes(form.entityKind)) {
      form.entityKind = metadata.value.entityKinds[0]
    }
    if (metadata.value.singletons.length > 0 && !metadata.value.singletons.includes(form.actorName)) {
      form.actorName = metadata.value.singletons[0]
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载脚本元数据失败')
  }
}

async function submitScript() {
  if (!form.scriptFile) {
    ElMessage.warning('请选择脚本文件')
    return
  }

  loading.value = true
  job.value = undefined
  items.value = []

  try {
    const createdJob = await createScriptJob({
      script: form.scriptFile,
      extra: form.extraFile,
      target: createTarget(),
      maxConcurrentItems: form.maxConcurrentItems,
    })
    job.value = createdJob
    items.value = (await listScriptJobItems(scriptIdValue(createdJob.id))).items
    ElMessage.success('脚本任务已创建')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '脚本任务提交失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadMetadata)
</script>

<template>
  <section class="script-layout">
    <el-form class="panel-card stack" label-position="top">
      <el-form-item label="执行目标">
        <el-segmented
          v-model="form.target"
          :options="[
            { label: '实体 Actor', value: 'entity' },
            { label: '单例 Actor', value: 'singleton' },
            { label: 'Actor Path', value: 'actor-paths' },
            { label: '节点', value: 'nodes' },
            { label: '角色节点', value: 'role' },
            { label: '全节点', value: 'all-nodes' },
          ]"
        />
      </el-form-item>

      <template v-if="form.target === 'entity'">
        <el-form-item label="Entity Kind">
          <el-select v-model="form.entityKind">
            <el-option
              v-for="entityKind in entityKindOptions"
              :key="entityKind"
              :label="entityKind"
              :value="entityKind"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Entity IDs">
          <el-input v-model="form.ids" placeholder="多个 id 用逗号分隔，例如 10001,10002" />
        </el-form-item>
      </template>

      <el-form-item v-if="form.target === 'singleton'" label="Singleton">
        <el-select v-model="form.actorName" filterable allow-create default-first-option>
          <el-option
            v-for="singleton in singletonOptions"
            :key="singleton"
            :label="singleton"
            :value="singleton"
          />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.target === 'actor-paths'" label="Actor Paths">
        <el-input
          v-model="form.actorPaths"
          type="textarea"
          :rows="4"
          placeholder="每行一个 actor path"
        />
      </el-form-item>

      <template v-if="form.target === 'nodes' || form.target === 'role'">
        <el-form-item v-if="form.target === 'role'" label="Role">
          <el-select v-model="form.role">
            <el-option
              v-for="role in roleOptions"
              :key="role"
              :label="role"
              :value="role"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.target === 'nodes'" label="Addresses">
          <el-input
            v-model="form.addresses"
            type="textarea"
            :rows="4"
            :placeholder="
              nodeAddressOptions.length > 0
                ? nodeAddressOptions.join('\n')
                : '每行一个 Pekko address'
            "
          />
        </el-form-item>
        <el-alert
          v-if="form.target === 'role'"
          title="角色节点任务会发往当前集群中全部匹配角色的节点。"
          type="info"
          :closable="false"
        />
      </template>

      <el-form-item label="最大并发项">
        <el-input-number v-model="form.maxConcurrentItems" :min="1" :step="1" />
      </el-form-item>

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
        提交任务
      </el-button>
    </el-form>

    <aside class="panel-card stack result-panel">
      <div>
        <p class="eyebrow">Result</p>
        <h2>脚本任务</h2>
      </div>

      <el-empty v-if="!job" description="暂无脚本任务" />
      <template v-else>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Job ID">
            <el-text tag="code">{{ scriptIdValue(job.id) }}</el-text>
          </el-descriptions-item>
          <el-descriptions-item label="Status">
            <el-tag>{{ job.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Items">
            {{ job.completedItems }} completed /
            {{ job.failedItems }} failed /
            {{ job.cancelledItems }} cancelled /
            {{ job.totalItems }} total
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="items" border>
          <el-table-column label="Target" min-width="180">
            <template #default="{ row }">
              {{ describeScriptTarget(row.target) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="Status" width="120" />
          <el-table-column label="Results" width="100">
            <template #default="{ row }">
              {{ row.results.length }}
            </template>
          </el-table-column>
          <el-table-column label="Error" min-width="220">
            <template #default="{ row }">
              {{ itemError(row) ?? '-' }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </aside>
  </section>
</template>

<style scoped>
.script-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: 16px;
  min-width: 0;
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
  font-size: 18px;
  letter-spacing: 0;
}

@media (max-width: 1180px) {
  .script-layout {
    grid-template-columns: 1fr;
  }
}
</style>
