<script setup lang="ts">
import { showError, showSuccess, showWarning } from '@/utils/feedback'
import { Document, UploadFilled } from '@element-plus/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getClusterStatus, type GmClusterNode } from '@/api/cluster'
import {
  applyEnabledPatches,
  applyPatch,
  createPatch,
  describePatchTarget,
  disablePatch,
  expireIncompatiblePatches,
  getPatch,
  listPatchNodeResults,
  listPatches,
  patchIdValue,
  type PatchClusterApplyResult,
  type PatchStatus,
  type RuntimePatchDescriptor,
  type RuntimePatchNodeResult,
} from '@/api/patch'

const loading = ref(false)
const createLoading = ref(false)
const clusterChoicesLoading = ref(false)
const patchHelpVisible = ref(false)
const patches = ref<RuntimePatchDescriptor[]>([])
const clusterNodes = ref<GmClusterNode[]>([])
const nodeResults = ref<RuntimePatchNodeResult[]>([])
const lastApply = ref<PatchClusterApplyResult | PatchClusterApplyResult[]>()
const selectedPatch = ref<RuntimePatchDescriptor>()
const { t } = useI18n()

const filters = reactive({
  status: '',
  appName: '',
  version: '',
})

const resultFilters = reactive({
  patchId: '',
  address: '',
  status: '',
})

const form = reactive({
  id: '',
  name: '',
  appName: 'akka-game-server',
  versions: '',
  artifactName: '',
  artifactVersion: '',
  targetType: 'all-nodes' as 'all-nodes' | 'roles' | 'nodes',
  roles: [] as string[],
  addresses: [] as string[],
  requiredRoles: [] as string[],
  requiredModules: [] as string[],
  requiredCapabilities: [] as string[],
  status: 'Draft' as PatchStatus,
  file: undefined as File | undefined,
})

const roleOptions = computed(() => uniqueSorted([
  ...clusterNodes.value.flatMap(node => node.roles ?? []),
  ...nodeResults.value.flatMap(result => result.roles ?? []),
]))

const nodeAddressOptions = computed(() => uniqueSorted([
  ...clusterNodes.value.map(node => node.address),
  ...nodeResults.value.map(result => result.address),
]))

const moduleOptions = computed(() => uniqueSorted([
  ...clusterNodes.value.flatMap(node => splitKnownValues(node.attributes?.['patch.modules'])),
  ...nodeResults.value.flatMap(result => result.modules ?? []),
]))

const capabilityOptions = computed(() => uniqueSorted([
  ...clusterNodes.value.flatMap(node => splitKnownValues(node.attributes?.['patch.capabilities'])),
  ...nodeResults.value.flatMap(result => result.capabilities ?? []),
]))

const applyResultRows = computed(() => {
  const result = lastApply.value
  if (!result) {
    return []
  }
  return Array.isArray(result) ? result.flatMap(item => item.results) : result.results
})

async function refreshPatches() {
  loading.value = true
  try {
    patches.value = await listPatches(filters)
  } catch (error) {
    showError(error, t('加载补丁列表失败'))
  } finally {
    loading.value = false
  }
}

async function refreshNodeResults() {
  try {
    nodeResults.value = await listPatchNodeResults(resultFilters)
  } catch (error) {
    showError(error, t('加载节点结果失败'))
  }
}

async function refreshClusterChoices() {
  clusterChoicesLoading.value = true
  try {
    const status = await getClusterStatus()
    clusterNodes.value = status.nodes ?? []
  } catch (error) {
    showError(error, t('加载补丁选项失败'))
  } finally {
    clusterChoicesLoading.value = false
  }
}

function onPatchFileChange(uploadFile: { raw?: File }) {
  form.file = uploadFile.raw
  if (!form.id && uploadFile.raw) {
    form.id = uploadFile.raw.name.replace(/\.[^.]+$/, '')
  }
  if (!form.name && uploadFile.raw) {
    form.name = form.id
  }
}

async function submitPatch() {
  const validationError = validatePatchForm()
  if (validationError) {
    showWarning(validationError)
    return
  }
  const patchFile = form.file
  if (!patchFile) {
    showWarning(t('请选择补丁 jar'))
    return
  }
  createLoading.value = true
  try {
    await createPatch({
      file: patchFile,
      id: form.id,
      name: form.name || form.id,
      appName: form.appName,
      versions: csv(form.versions),
      artifactName: form.artifactName,
      artifactVersion: form.artifactVersion,
      targetType: form.targetType,
      roles: form.roles,
      addresses: form.addresses,
      requiredRoles: form.requiredRoles,
      requiredModules: form.requiredModules,
      requiredCapabilities: form.requiredCapabilities,
      status: form.status,
    })
    showSuccess(t('补丁已发布'))
    await refreshPatches()
  } catch (error) {
    showError(error, t('发布补丁失败'))
  } finally {
    createLoading.value = false
  }
}

async function runApply(id: string) {
  loading.value = true
  try {
    lastApply.value = await applyPatch(id)
    showSuccess(t('补丁 apply 已提交'))
    resultFilters.patchId = id
    await Promise.all([refreshPatches(), refreshNodeResults()])
  } catch (error) {
    showError(error, t('补丁 apply 失败'))
  } finally {
    loading.value = false
  }
}

async function loadPatchDetail(id: string) {
  try {
    selectedPatch.value = await getPatch(id)
  } catch (error) {
    showError(error, t('加载补丁详情失败'))
  }
}

async function runDisable(id: string) {
  loading.value = true
  try {
    await disablePatch(id)
    showSuccess(t('补丁已禁用'))
    resultFilters.patchId = id
    await Promise.all([refreshPatches(), refreshNodeResults()])
  } catch (error) {
    showError(error, t('禁用补丁失败'))
  } finally {
    loading.value = false
  }
}

async function runApplyEnabled() {
  loading.value = true
  try {
    lastApply.value = await applyEnabledPatches()
    showSuccess(t('已 apply 所有启用补丁'))
    await refreshNodeResults()
  } catch (error) {
    showError(error, t('apply 启用补丁失败'))
  } finally {
    loading.value = false
  }
}

async function runExpireIncompatible() {
  loading.value = true
  try {
    await expireIncompatiblePatches()
    showSuccess(t('已过期不兼容补丁'))
    await refreshPatches()
  } catch (error) {
    showError(error, t('过期补丁失败'))
  } finally {
    loading.value = false
  }
}

function csv(raw: string) {
  return raw.split(',').map(item => item.trim()).filter(Boolean)
}

function splitKnownValues(raw?: string | null) {
  return (raw ?? '')
    .replace(/^[\[\(]|[\]\)]$/g, '')
    .split(/[,\s;]+/)
    .map(item => item.trim().replace(/^["']|["']$/g, ''))
    .filter(Boolean)
}

function uniqueSorted(values: string[]) {
  return [...new Set(values.map(value => value.trim()).filter(Boolean))].sort((left, right) => left.localeCompare(right))
}

function versionsText(patch: RuntimePatchDescriptor) {
  return patch.compatibility.versions.map(String).join(', ')
}

function requirementText(values: unknown[]) {
  return values.map(itemText).join(', ') || '-'
}

function itemText(value: unknown) {
  if (typeof value === 'string') {
    return value
  }
  if (value && typeof value === 'object' && 'value' in value) {
    return itemText((value as { value?: unknown }).value)
  }
  return String(value)
}

function jsonText(value: unknown) {
  return value == null ? '-' : JSON.stringify(value, null, 2)
}

function validatePatchForm() {
  if (!form.file) {
    return t('请选择补丁 jar')
  }
  const missing: string[] = []
  if (!form.id.trim()) {
    missing.push('ID')
  }
  if (!form.appName.trim()) {
    missing.push(t('应用名称'))
  }
  if (csv(form.versions).length === 0) {
    missing.push(t('版本'))
  }
  if (form.targetType === 'roles' && form.roles.length === 0) {
    missing.push(t('目标 Role'))
  }
  if (form.targetType === 'nodes' && form.addresses.length === 0) {
    missing.push(t('目标地址'))
  }
  return missing.length > 0 ? t('请填写必填项：{fields}', { fields: missing.join(', ') }) : ''
}

function patchStatusText(status: string) {
  const labels: Record<string, string> = {
    Draft: t('草稿'),
    Enabled: t('已启用'),
    Disabled: t('已禁用'),
    Expired: t('已过期'),
    Failed: t('失败'),
  }
  return labels[status] ?? status
}

function nodeResultStatusText(status: string) {
  const labels: Record<string, string> = {
    Applied: t('已应用'),
    Removed: t('已移除'),
    Ignored: t('已忽略'),
    Failed: t('失败'),
    Unreachable: t('不可达'),
  }
  return labels[status] ?? status
}

onMounted(async () => {
  await Promise.all([refreshPatches(), refreshNodeResults(), refreshClusterChoices()])
})
</script>

<template>
  <section class="stack">
      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('运行时 Patch') }}</p>
            <h2>{{ t('补丁列表') }}</h2>
          </div>
          <el-space wrap>
            <el-button :loading="loading" @click="refreshPatches">{{ t('刷新') }}</el-button>
            <el-button :loading="loading" type="primary" @click="runApplyEnabled">{{ t('Apply 已启用补丁') }}</el-button>
            <el-button :loading="loading" @click="runExpireIncompatible">{{ t('过期不兼容补丁') }}</el-button>
          </el-space>
        </div>

        <el-form class="inline-form" label-position="top">
          <el-form-item :label="t('状态')">
            <el-select v-model="filters.status" clearable>
              <el-option :label="t('草稿')" value="Draft" />
              <el-option :label="t('已启用')" value="Enabled" />
              <el-option :label="t('已禁用')" value="Disabled" />
              <el-option :label="t('已过期')" value="Expired" />
              <el-option :label="t('失败')" value="Failed" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('应用')">
            <el-input v-model="filters.appName" />
          </el-form-item>
          <el-form-item :label="t('版本')">
            <el-input v-model="filters.version" />
          </el-form-item>
          <el-form-item label=" ">
            <el-button :loading="loading" @click="refreshPatches">{{ t('查询') }}</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="patches" border>
          <el-table-column label="ID" min-width="170">
            <template #default="{ row }">
              {{ patchIdValue(row.id) }}
            </template>
          </el-table-column>
          <el-table-column prop="name" :label="t('名称')" min-width="160" />
          <el-table-column :label="t('目标')" min-width="160">
            <template #default="{ row }">
              {{ describePatchTarget(row.target) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('状态')" width="110">
            <template #default="{ row }">
              {{ patchStatusText(row.status) }}
            </template>
          </el-table-column>
          <el-table-column prop="revision" label="Revision" width="110" />
          <el-table-column :label="t('版本')" min-width="180">
            <template #default="{ row }">
              {{ versionsText(row) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('运行要求')" min-width="220">
            <template #default="{ row }">
              Role={{ requirementText(row.requirements.roles) }};
              {{ t('模块') }}={{ requirementText(row.requirements.modules) }};
              {{ t('能力') }}={{ requirementText(row.requirements.capabilities) }}
            </template>
          </el-table-column>
          <el-table-column :label="t('操作')" width="250" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="loadPatchDetail(patchIdValue(row.id))">
                {{ t('详情') }}
              </el-button>
              <el-button size="small" :loading="loading" @click="runApply(patchIdValue(row.id))">
                Apply
              </el-button>
              <el-button size="small" type="warning" :loading="loading" @click="runDisable(patchIdValue(row.id))">
                {{ t('禁用') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('节点结果') }}</p>
            <h2>{{ t('节点结果') }}</h2>
          </div>
          <el-button :loading="loading" @click="refreshNodeResults">{{ t('刷新') }}</el-button>
        </div>
        <el-form class="inline-form" label-position="top">
          <el-form-item label="Patch ID">
            <el-input v-model="resultFilters.patchId" />
          </el-form-item>
          <el-form-item :label="t('地址')">
            <el-input v-model="resultFilters.address" />
          </el-form-item>
          <el-form-item :label="t('状态')">
            <el-select v-model="resultFilters.status" clearable>
              <el-option :label="t('已应用')" value="Applied" />
              <el-option :label="t('已移除')" value="Removed" />
              <el-option :label="t('已忽略')" value="Ignored" />
              <el-option :label="t('失败')" value="Failed" />
              <el-option :label="t('不可达')" value="Unreachable" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="nodeResults" border>
          <el-table-column label="Patch" min-width="160">
            <template #default="{ row }">
              {{ patchIdValue(row.patchId) }}
            </template>
          </el-table-column>
          <el-table-column prop="address" :label="t('地址')" min-width="220" />
          <el-table-column :label="t('状态')" width="120">
            <template #default="{ row }">
              {{ nodeResultStatusText(row.status) }}
            </template>
          </el-table-column>
          <el-table-column prop="attempt" :label="t('尝试次数')" width="100" />
          <el-table-column prop="message" :label="t('消息')" min-width="220" />
        </el-table>
      </div>

    <div class="two-column-grid">
      <el-form class="panel-card stack" label-position="top">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('发布') }}</p>
            <h2>{{ t('发布补丁') }}</h2>
          </div>
          <el-space wrap>
            <el-button :loading="clusterChoicesLoading" @click="refreshClusterChoices">{{ t('刷新选项') }}</el-button>
            <el-button :icon="Document" @click="patchHelpVisible = true">{{ t('详细说明') }}</el-button>
          </el-space>
        </div>
        <el-form-item label="Patch Jar" required>
          <el-upload drag action="#" :auto-upload="false" :limit="1" :on-change="onPatchFileChange">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">{{ t('拖入补丁 jar，或点击选择') }}</div>
          </el-upload>
          <p class="field-help">{{ t('补丁 jar 是实际要发布的运行时代码包，选择后会自动带出默认 ID 和名称。') }}</p>
        </el-form-item>
        <el-form-item label="ID" required>
          <el-input v-model="form.id" :placeholder="t('全局唯一，例如 hotfix-battle-20260513')" />
          <p class="field-help">{{ t('ID 是补丁的稳定唯一标识，后续 apply、禁用、节点结果查询都会使用它；重复 ID 会覆盖或更新同一个补丁记录。') }}</p>
        </el-form-item>
        <el-form-item :label="t('名称')">
          <el-input v-model="form.name" :placeholder="t('留空使用 ID')" />
          <p class="field-help">{{ t('名称用于列表展示和人工识别，不参与补丁匹配；留空会使用 ID。') }}</p>
        </el-form-item>
        <el-form-item :label="t('应用名称')" required>
          <el-input v-model="form.appName" />
          <p class="field-help">{{ t('应用名称必须和目标节点运行时的应用名一致，不一致时补丁会被判定为不兼容。') }}</p>
        </el-form-item>
        <el-form-item :label="t('版本')" required>
          <el-input v-model="form.versions" :placeholder="t('逗号分隔')" />
          <p class="field-help">{{ t('版本是允许应用补丁的服务端版本列表；目标节点版本不在列表中会被忽略或过期。') }}</p>
        </el-form-item>
        <el-form-item :label="t('Artifact 名称')">
          <el-input v-model="form.artifactName" :placeholder="t('留空使用补丁 ID')" />
          <p class="field-help">{{ t('Artifact 是补丁 jar 在存储层里的产物记录；名称用于标识这份 jar，通常填构建产物名或模块名，不决定下发目标。') }}</p>
        </el-form-item>
        <el-form-item :label="t('Artifact 版本')">
          <el-input v-model="form.artifactVersion" />
          <p class="field-help">{{ t('Artifact 版本用于标记这份 jar 的产物版本，便于审计、回溯和排查；是否能应用仍由应用名称、服务端版本和运行要求决定。') }}</p>
        </el-form-item>
        <el-form-item :label="t('目标')">
          <el-segmented
            v-model="form.targetType"
            :options="[
              { label: t('全部'), value: 'all-nodes' },
              { label: 'Role', value: 'roles' },
              { label: t('节点'), value: 'nodes' },
            ]"
          />
          <p class="field-help">{{ t('目标决定补丁会尝试下发到哪些节点；选择范围过大会让所有匹配节点都执行 apply。') }}</p>
        </el-form-item>
        <el-form-item v-if="form.targetType === 'roles'" :label="t('目标 Role')" required>
          <el-select
            v-model="form.roles"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('选择 Role')"
          >
            <el-option v-for="role in roleOptions" :key="role" :label="role" :value="role" />
          </el-select>
          <p class="field-help">{{ t('可从当前集群已知 Role 中选择，也可以手动输入未来或暂未上线的 Role。') }}</p>
        </el-form-item>
        <el-form-item v-if="form.targetType === 'nodes'" :label="t('目标地址')" required>
          <el-select
            v-model="form.addresses"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('选择节点地址')"
          >
            <el-option v-for="address in nodeAddressOptions" :key="address" :label="address" :value="address" />
          </el-select>
          <p class="field-help">{{ t('可从当前集群节点地址中选择，也可以手动输入尚未被发现的地址。') }}</p>
        </el-form-item>
        <el-form-item :label="t('要求 Role')">
          <el-select
            v-model="form.requiredRoles"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('选择 Role')"
          >
            <el-option v-for="role in roleOptions" :key="role" :label="role" :value="role" />
          </el-select>
          <p class="field-help">{{ t('要求 Role 是 apply 前的二次校验，目标节点必须同时具备这些 Role；通常用于确保补丁只在具备特定职责的节点生效。') }}</p>
        </el-form-item>
        <el-form-item :label="t('要求模块')">
          <el-select
            v-model="form.requiredModules"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('选择模块')"
          >
            <el-option v-for="module in moduleOptions" :key="module" :label="module" :value="module" />
          </el-select>
          <p class="field-help">{{ t('模块选项来自节点上报的 patch.modules；没有选项时说明当前节点未上报，可以手动输入或留空。') }}</p>
        </el-form-item>
        <el-form-item :label="t('要求能力')">
          <el-select
            v-model="form.requiredCapabilities"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="t('选择能力')"
          >
            <el-option v-for="capability in capabilityOptions" :key="capability" :label="capability" :value="capability" />
          </el-select>
          <p class="field-help">{{ t('能力选项来自节点上报的 patch.capabilities；没有选项时说明当前节点未上报，可以手动输入或留空。') }}</p>
        </el-form-item>
        <el-form-item :label="t('状态')">
          <el-select v-model="form.status">
            <el-option :label="t('草稿')" value="Draft" />
            <el-option :label="t('已启用')" value="Enabled" />
          </el-select>
          <p class="field-help">{{ t('草稿只保存记录，不会被批量 apply；已启用会参与“Apply 已启用补丁”。') }}</p>
        </el-form-item>
        <el-button type="primary" :loading="createLoading" @click="submitPatch">{{ t('发布') }}</el-button>
      </el-form>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">Patch {{ t('详情') }}</p>
          <h2>{{ t('补丁详情') }}</h2>
        </div>
        <el-empty v-if="!selectedPatch" :description="t('选择补丁查看详情')" />
        <template v-else>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="ID">{{ patchIdValue(selectedPatch.id) }}</el-descriptions-item>
            <el-descriptions-item :label="t('名称')">{{ selectedPatch.name }}</el-descriptions-item>
            <el-descriptions-item :label="t('状态')">{{ patchStatusText(selectedPatch.status) }}</el-descriptions-item>
            <el-descriptions-item :label="t('目标')">{{ describePatchTarget(selectedPatch.target) }}</el-descriptions-item>
            <el-descriptions-item label="Revision">{{ selectedPatch.revision }}</el-descriptions-item>
          </el-descriptions>
          <pre class="raw-output">{{ jsonText(selectedPatch) }}</pre>
        </template>
      </div>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">{{ t('Apply 结果') }}</p>
          <h2>{{ t('最近执行') }}</h2>
        </div>
        <el-empty v-if="applyResultRows.length === 0" :description="t('暂无执行结果')" />
        <el-table v-else :data="applyResultRows" border>
          <el-table-column prop="address" :label="t('地址')" min-width="180" />
          <el-table-column :label="t('状态')" width="120">
            <template #default="{ row }">
              {{ nodeResultStatusText(row.status) }}
            </template>
          </el-table-column>
          <el-table-column prop="message" :label="t('消息')" min-width="180" />
        </el-table>
      </div>
    </div>

    <el-drawer v-model="patchHelpVisible" :title="t('运行补丁说明')" size="420px">
      <div class="help-doc">
        <section>
          <h3>{{ t('发布前检查') }}</h3>
          <ul>
            <li>{{ t('确认补丁 jar 来自正确分支和版本，避免把不兼容代码发布到线上节点。') }}</li>
            <li>{{ t('确认应用名称和版本列表能覆盖目标节点，否则补丁会被兼容性检查跳过。') }}</li>
            <li>{{ t('先以草稿发布并查看详情，确认 ID、目标和要求无误后再启用。') }}</li>
          </ul>
        </section>
        <section>
          <h3>{{ t('Artifact 是什么') }}</h3>
          <ul>
            <li>{{ t('Patch ID 是 GM 仓库里的补丁记录 ID；Artifact 是补丁 jar 在 artifact store 里的产物信息。') }}</li>
            <li>{{ t('发布时后端会保存 jar，生成 checksum，并把 Artifact 名称、版本和 checksum 写入补丁描述。') }}</li>
            <li>{{ t('节点 apply 时会通过 Artifact 找到 jar 内容并校验 checksum；Artifact 名称和版本主要用于识别、审计和排查。') }}</li>
          </ul>
        </section>
        <section>
          <h3>{{ t('目标和运行要求') }}</h3>
          <ul>
            <li>{{ t('目标是第一层筛选：全部节点、指定 Role、或指定节点地址，决定补丁会尝试发给谁。') }}</li>
            <li>{{ t('运行要求是第二层筛选：目标节点必须同时满足要求 Role、要求模块、要求能力，才会真正 apply。') }}</li>
            <li>{{ t('要求模块来自节点上报的 patch.modules，要求能力来自节点上报的 patch.capabilities，字段值需要完全一致。') }}</li>
            <li>{{ t('如果补丁 jar manifest 中声明了 Asteria-Patch-Roles、Asteria-Patch-Modules、Asteria-Patch-Capabilities，框架也会使用这些信息作为要求。') }}</li>
          </ul>
        </section>
        <section>
          <h3>{{ t('填错后的影响') }}</h3>
          <ul>
            <li>{{ t('应用名称或版本填错会让节点判定不兼容，补丁不会生效。') }}</li>
            <li>{{ t('目标地址或 Role 填错会让补丁发到错误范围，可能漏发或误发。') }}</li>
            <li>{{ t('要求模块或要求能力填错会导致节点不满足要求，即使目标命中了也会跳过 apply。') }}</li>
            <li>{{ t('运行要求留空表示不做这层限制，适合无法确认节点上报模块/能力时先以目标范围控制。') }}</li>
          </ul>
        </section>
      </div>
    </el-drawer>
  </section>
</template>
