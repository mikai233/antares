<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
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
const patches = ref<RuntimePatchDescriptor[]>([])
const nodeResults = ref<RuntimePatchNodeResult[]>([])
const lastApply = ref<PatchClusterApplyResult | PatchClusterApplyResult[]>()
const selectedPatch = ref<RuntimePatchDescriptor>()

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
  roles: '',
  addresses: '',
  requiredRoles: '',
  requiredModules: '',
  requiredCapabilities: '',
  status: 'Draft' as PatchStatus,
  file: undefined as File | undefined,
})

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
    ElMessage.error(error instanceof Error ? error.message : '加载补丁列表失败')
  } finally {
    loading.value = false
  }
}

async function refreshNodeResults() {
  try {
    nodeResults.value = await listPatchNodeResults(resultFilters)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载节点结果失败')
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
  if (!form.file) {
    ElMessage.warning('请选择补丁 jar')
    return
  }
  createLoading.value = true
  try {
    await createPatch({
      file: form.file,
      id: form.id,
      name: form.name || form.id,
      appName: form.appName,
      versions: csv(form.versions),
      artifactName: form.artifactName,
      artifactVersion: form.artifactVersion,
      targetType: form.targetType,
      roles: csv(form.roles),
      addresses: lines(form.addresses),
      requiredRoles: csv(form.requiredRoles),
      requiredModules: csv(form.requiredModules),
      requiredCapabilities: csv(form.requiredCapabilities),
      status: form.status,
    })
    ElMessage.success('补丁已发布')
    await refreshPatches()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布补丁失败')
  } finally {
    createLoading.value = false
  }
}

async function runApply(id: string) {
  loading.value = true
  try {
    lastApply.value = await applyPatch(id)
    ElMessage.success('补丁 apply 已提交')
    resultFilters.patchId = id
    await Promise.all([refreshPatches(), refreshNodeResults()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '补丁 apply 失败')
  } finally {
    loading.value = false
  }
}

async function loadPatchDetail(id: string) {
  try {
    selectedPatch.value = await getPatch(id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载补丁详情失败')
  }
}

async function runDisable(id: string) {
  loading.value = true
  try {
    await disablePatch(id)
    ElMessage.success('补丁已禁用')
    resultFilters.patchId = id
    await Promise.all([refreshPatches(), refreshNodeResults()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '禁用补丁失败')
  } finally {
    loading.value = false
  }
}

async function runApplyEnabled() {
  loading.value = true
  try {
    lastApply.value = await applyEnabledPatches()
    ElMessage.success('已 apply 所有启用补丁')
    await refreshNodeResults()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'apply 启用补丁失败')
  } finally {
    loading.value = false
  }
}

async function runExpireIncompatible() {
  loading.value = true
  try {
    await expireIncompatiblePatches()
    ElMessage.success('已过期不兼容补丁')
    await refreshPatches()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '过期补丁失败')
  } finally {
    loading.value = false
  }
}

function csv(raw: string) {
  return raw.split(',').map(item => item.trim()).filter(Boolean)
}

function lines(raw: string) {
  return raw.split('\n').map(item => item.trim()).filter(Boolean)
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

onMounted(async () => {
  await Promise.all([refreshPatches(), refreshNodeResults()])
})
</script>

<template>
  <section class="page-grid">
    <div class="stack">
      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Runtime Patch</p>
            <h2>补丁列表</h2>
          </div>
          <el-space wrap>
            <el-button :loading="loading" @click="refreshPatches">刷新</el-button>
            <el-button :loading="loading" type="primary" @click="runApplyEnabled">Apply Enabled</el-button>
            <el-button :loading="loading" @click="runExpireIncompatible">Expire Incompatible</el-button>
          </el-space>
        </div>

        <el-form class="inline-form" label-position="top">
          <el-form-item label="Status">
            <el-select v-model="filters.status" clearable>
              <el-option label="Draft" value="Draft" />
              <el-option label="Enabled" value="Enabled" />
              <el-option label="Disabled" value="Disabled" />
              <el-option label="Expired" value="Expired" />
              <el-option label="Failed" value="Failed" />
            </el-select>
          </el-form-item>
          <el-form-item label="App">
            <el-input v-model="filters.appName" />
          </el-form-item>
          <el-form-item label="Version">
            <el-input v-model="filters.version" />
          </el-form-item>
          <el-form-item label=" ">
            <el-button :loading="loading" @click="refreshPatches">查询</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="loading" :data="patches" border>
          <el-table-column label="ID" min-width="170">
            <template #default="{ row }">
              {{ patchIdValue(row.id) }}
            </template>
          </el-table-column>
          <el-table-column prop="name" label="Name" min-width="160" />
          <el-table-column label="Target" min-width="160">
            <template #default="{ row }">
              {{ describePatchTarget(row.target) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="Status" width="110" />
          <el-table-column prop="revision" label="Revision" width="110" />
          <el-table-column label="Versions" min-width="180">
            <template #default="{ row }">
              {{ versionsText(row) }}
            </template>
          </el-table-column>
          <el-table-column label="Requirements" min-width="220">
            <template #default="{ row }">
              roles={{ requirementText(row.requirements.roles) }};
              modules={{ requirementText(row.requirements.modules) }};
              capabilities={{ requirementText(row.requirements.capabilities) }}
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="250" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="loadPatchDetail(patchIdValue(row.id))">
                Detail
              </el-button>
              <el-button size="small" :loading="loading" @click="runApply(patchIdValue(row.id))">
                Apply
              </el-button>
              <el-button size="small" type="warning" :loading="loading" @click="runDisable(patchIdValue(row.id))">
                Disable
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel-card stack">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Node Results</p>
            <h2>节点结果</h2>
          </div>
          <el-button :loading="loading" @click="refreshNodeResults">刷新</el-button>
        </div>
        <el-form class="inline-form" label-position="top">
          <el-form-item label="Patch ID">
            <el-input v-model="resultFilters.patchId" />
          </el-form-item>
          <el-form-item label="Address">
            <el-input v-model="resultFilters.address" />
          </el-form-item>
          <el-form-item label="Status">
            <el-select v-model="resultFilters.status" clearable>
              <el-option label="Applied" value="Applied" />
              <el-option label="Removed" value="Removed" />
              <el-option label="Ignored" value="Ignored" />
              <el-option label="Failed" value="Failed" />
              <el-option label="Unreachable" value="Unreachable" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="nodeResults" border>
          <el-table-column label="Patch" min-width="160">
            <template #default="{ row }">
              {{ patchIdValue(row.patchId) }}
            </template>
          </el-table-column>
          <el-table-column prop="address" label="Address" min-width="220" />
          <el-table-column prop="status" label="Status" width="120" />
          <el-table-column prop="attempt" label="Attempt" width="100" />
          <el-table-column prop="message" label="Message" min-width="220" />
        </el-table>
      </div>
    </div>

    <aside class="stack">
      <el-form class="panel-card stack" label-position="top">
        <div>
          <p class="eyebrow">Publish</p>
          <h2>发布补丁</h2>
        </div>
        <el-form-item label="Patch Jar">
          <el-upload action="#" :auto-upload="false" :limit="1" :on-change="onPatchFileChange">
            <el-button>选择 jar</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="ID">
          <el-input v-model="form.id" />
        </el-form-item>
        <el-form-item label="Name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="App Name">
          <el-input v-model="form.appName" />
        </el-form-item>
        <el-form-item label="Versions">
          <el-input v-model="form.versions" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="Artifact Name">
          <el-input v-model="form.artifactName" />
        </el-form-item>
        <el-form-item label="Artifact Version">
          <el-input v-model="form.artifactVersion" />
        </el-form-item>
        <el-form-item label="Target">
          <el-segmented
            v-model="form.targetType"
            :options="[
              { label: 'All', value: 'all-nodes' },
              { label: 'Roles', value: 'roles' },
              { label: 'Nodes', value: 'nodes' },
            ]"
          />
        </el-form-item>
        <el-form-item v-if="form.targetType === 'roles'" label="Target Roles">
          <el-input v-model="form.roles" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item v-if="form.targetType === 'nodes'" label="Target Addresses">
          <el-input v-model="form.addresses" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="Required Roles">
          <el-input v-model="form.requiredRoles" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="Required Modules">
          <el-input v-model="form.requiredModules" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="Required Capabilities">
          <el-input v-model="form.requiredCapabilities" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="form.status">
            <el-option label="Draft" value="Draft" />
            <el-option label="Enabled" value="Enabled" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="createLoading" @click="submitPatch">发布</el-button>
      </el-form>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">Patch Detail</p>
          <h2>补丁详情</h2>
        </div>
        <el-empty v-if="!selectedPatch" description="选择补丁查看详情" />
        <template v-else>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="ID">{{ patchIdValue(selectedPatch.id) }}</el-descriptions-item>
            <el-descriptions-item label="Name">{{ selectedPatch.name }}</el-descriptions-item>
            <el-descriptions-item label="Status">{{ selectedPatch.status }}</el-descriptions-item>
            <el-descriptions-item label="Target">{{ describePatchTarget(selectedPatch.target) }}</el-descriptions-item>
            <el-descriptions-item label="Revision">{{ selectedPatch.revision }}</el-descriptions-item>
          </el-descriptions>
          <pre class="raw-output">{{ jsonText(selectedPatch) }}</pre>
        </template>
      </div>

      <div class="panel-card stack">
        <div>
          <p class="eyebrow">Apply Result</p>
          <h2>最近执行</h2>
        </div>
        <el-empty v-if="applyResultRows.length === 0" description="暂无执行结果" />
        <el-table v-else :data="applyResultRows" border>
          <el-table-column prop="address" label="Address" min-width="180" />
          <el-table-column prop="status" label="Status" width="120" />
          <el-table-column prop="message" label="Message" min-width="180" />
        </el-table>
      </div>
    </aside>
  </section>
</template>
