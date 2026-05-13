<script setup lang="ts">
import { showError, showSuccess, showWarning } from '@/utils/feedback'
import { UploadFilled } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
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
const { t } = useI18n()

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

function scriptJobStatusText(status: string) {
  const labels: Record<string, string> = {
    Pending: t('等待中'),
    Running: t('执行中'),
    Completed: t('已完成'),
    Failed: t('失败'),
    Cancelled: t('已取消'),
  }
  return labels[status] ?? status
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
    if (form.maxConcurrentItems == null) {
      form.maxConcurrentItems = metadata.value.defaultMaxConcurrentItems
    }
  } catch (error) {
    showError(error, t('加载脚本元数据失败'))
  }
}

async function submitScript() {
  const validationError = validateScriptForm()
  if (validationError) {
    showWarning(validationError)
    return
  }
  const scriptFile = form.scriptFile
  if (!scriptFile) {
    showWarning(t('请选择脚本文件'))
    return
  }

  loading.value = true
  job.value = undefined
  items.value = []

  try {
    const createdJob = await createScriptJob({
      script: scriptFile,
      extra: form.extraFile,
      target: createTarget(),
      maxConcurrentItems: form.maxConcurrentItems,
    })
    job.value = createdJob
    items.value = (await listScriptJobItems(scriptIdValue(createdJob.id))).items
    showSuccess(t('脚本任务已创建'))
  } catch (error) {
    showError(error, t('脚本任务提交失败'))
  } finally {
    loading.value = false
  }
}

function validateScriptForm() {
  const missing: string[] = []
  if (!form.scriptFile) {
    missing.push(t('脚本文件'))
  }
  if (form.target === 'entity' && splitCsv(form.ids).length === 0) {
    missing.push(t('实体 ID'))
  }
  if (form.target === 'singleton' && !form.actorName.trim()) {
    missing.push(t('单例 Actor'))
  }
  if (form.target === 'actor-paths' && splitLines(form.actorPaths).length === 0) {
    missing.push('Actor Path')
  }
  if (form.target === 'nodes' && splitLines(form.addresses).length === 0) {
    missing.push(t('地址'))
  }
  return missing.length > 0 ? t('请填写必填项：{fields}', { fields: missing.join(', ') }) : ''
}

onMounted(loadMetadata)
</script>

<template>
  <section class="stack">
    <el-form class="panel-card stack" label-position="top">
      <el-form-item :label="t('执行目标')">
        <el-segmented
          v-model="form.target"
          :options="[
            { label: t('实体 Actor'), value: 'entity' },
            { label: t('单例 Actor'), value: 'singleton' },
            { label: 'Actor Path', value: 'actor-paths' },
            { label: t('节点'), value: 'nodes' },
            { label: t('角色节点'), value: 'role' },
            { label: t('全节点'), value: 'all-nodes' },
          ]"
        />
        <p class="field-help">{{ t('执行目标决定脚本会发往哪些 Actor 或节点；目标范围越大，执行影响面越大。') }}</p>
      </el-form-item>

      <template v-if="form.target === 'entity'">
        <el-form-item :label="t('实体类型')">
          <el-select v-model="form.entityKind">
            <el-option
              v-for="entityKind in entityKindOptions"
              :key="entityKind"
              :label="entityKind"
              :value="entityKind"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('实体 ID')" required>
          <el-input v-model="form.ids" :placeholder="t('多个 id 用逗号分隔，例如 10001,10002')" />
          <p class="field-help">{{ t('实体 ID 会被解析为目标 Actor，填错会导致脚本发到错误实体或找不到目标。') }}</p>
        </el-form-item>
      </template>

      <el-form-item v-if="form.target === 'singleton'" :label="t('单例 Actor')" required>
        <el-select v-model="form.actorName" filterable allow-create default-first-option>
          <el-option
            v-for="singleton in singletonOptions"
            :key="singleton"
            :label="singleton"
            :value="singleton"
          />
        </el-select>
        <p class="field-help">{{ t('单例 Actor 名称必须和服务端注册名一致，填错会找不到目标。') }}</p>
      </el-form-item>

      <el-form-item v-if="form.target === 'actor-paths'" label="Actor Path" required>
        <el-input
          v-model="form.actorPaths"
          type="textarea"
          :rows="4"
          :placeholder="t('每行一个 actor path')"
        />
        <p class="field-help">{{ t('Actor Path 是精确投递地址，请逐行填写完整路径。') }}</p>
      </el-form-item>

      <template v-if="form.target === 'nodes' || form.target === 'role'">
        <el-form-item v-if="form.target === 'role'" label="Role" required>
          <el-select v-model="form.role">
            <el-option
              v-for="role in roleOptions"
              :key="role"
              :label="role"
              :value="role"
            />
          </el-select>
          <p class="field-help">{{ t('Role 目标会在当前集群内匹配所有对应角色节点。') }}</p>
        </el-form-item>
        <el-form-item v-if="form.target === 'nodes'" :label="t('地址')" required>
          <el-input
            v-model="form.addresses"
            type="textarea"
            :rows="4"
            :placeholder="
              nodeAddressOptions.length > 0
                ? nodeAddressOptions.join('\n')
                : t('每行一个 Pekko 地址')
            "
          />
          <p class="field-help">{{ t('节点地址按行填写，填错会导致该节点不会执行脚本。') }}</p>
        </el-form-item>
        <el-alert
          v-if="form.target === 'role'"
          :title="t('角色节点任务会发往当前集群中全部匹配角色的节点。')"
          type="info"
          :closable="false"
        />
      </template>

      <el-form-item :label="t('最大并发项')">
        <el-input-number v-model="form.maxConcurrentItems" :min="1" :step="1" />
        <p class="field-help">{{ t('限制同一个脚本任务同时执行的任务项数量，值越大执行越快但对集群压力越高。') }}</p>
      </el-form-item>

      <el-form-item :label="t('脚本文件')" required>
        <el-upload
          drag
          action="#"
          :auto-upload="false"
          :limit="1"
          :on-change="onScriptFileChange"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">{{ t('拖入 jar/groovy 文件，或点击选择') }}</div>
        </el-upload>
        <p class="field-help">{{ t('脚本文件是实际执行内容，仅支持 jar 或 groovy。') }}</p>
      </el-form-item>

      <el-form-item :label="t('额外附件')">
        <el-upload
          action="#"
          :auto-upload="false"
          :limit="1"
          :on-change="onExtraFileChange"
        >
          <el-button>{{ t('选择附件') }}</el-button>
        </el-upload>
        <p class="field-help">{{ t('额外附件会随脚本一起提交，供脚本运行时读取。') }}</p>
      </el-form-item>

      <el-button type="primary" size="large" :loading="loading" @click="submitScript">
        {{ t('提交任务') }}
      </el-button>
    </el-form>

    <div class="panel-card stack result-panel">
      <div>
        <p class="eyebrow">{{ t('结果') }}</p>
        <h2>{{ t('脚本任务') }}</h2>
      </div>

      <el-empty v-if="!job" :description="t('暂无脚本任务')" />
      <template v-else>
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('任务 ID')">
            <el-text tag="code">{{ scriptIdValue(job.id) }}</el-text>
          </el-descriptions-item>
          <el-descriptions-item :label="t('状态')">
            <el-tag>{{ scriptJobStatusText(job.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('任务项')">
            {{ job.completedItems }} {{ t('已完成') }} /
            {{ job.failedItems }} {{ t('失败') }} /
            {{ job.cancelledItems }} {{ t('已取消') }} /
            {{ job.totalItems }} {{ t('总数') }}
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="items" border>
          <el-table-column :label="t('目标')" min-width="180">
            <template #default="{ row }">
              {{ describeScriptTarget(row.target) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('状态')" width="120">
            <template #default="{ row }">
              {{ scriptJobStatusText(row.status) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('结果数')" width="100">
            <template #default="{ row }">
              {{ row.results?.length ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column :label="t('错误')" min-width="220">
            <template #default="{ row }">
              {{ itemError(row) ?? '-' }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </section>
</template>

<style scoped>
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

</style>
