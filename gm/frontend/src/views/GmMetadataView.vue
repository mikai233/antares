<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  gmId,
  listGmActions,
  listGmFeatures,
  listGmMenus,
  listGmRoutes,
  type GmActionDescriptor,
  type GmFeatureDescriptor,
  type GmMenuItem,
  type GmRoute,
} from '@/api/gm'

const loading = ref(false)
const features = ref<GmFeatureDescriptor[]>([])
const menus = ref<GmMenuItem[]>([])
const routes = ref<GmRoute[]>([])
const actions = ref<GmActionDescriptor[]>([])
const { t } = useI18n()

async function refresh() {
  loading.value = true
  try {
    const [nextFeatures, nextMenus, nextRoutes, nextActions] = await Promise.all([
      listGmFeatures(),
      listGmMenus(),
      listGmRoutes(),
      listGmActions(),
    ])
    features.value = nextFeatures
    menus.value = nextMenus
    routes.value = nextRoutes
    actions.value = nextActions
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('加载 GM 元数据失败'))
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <section class="stack">
    <div class="panel-card section-heading">
      <div>
        <p class="eyebrow">Asteria GM</p>
        <h2>{{ t('能力元数据') }}</h2>
      </div>
      <el-button :loading="loading" @click="refresh">{{ t('刷新') }}</el-button>
    </div>

    <div class="two-column-grid">
      <div class="panel-card stack">
        <h2>{{ t('功能') }}</h2>
        <el-table v-loading="loading" :data="features" border>
          <el-table-column label="ID" min-width="160">
            <template #default="{ row }">
              {{ gmId(row.id) }}
            </template>
          </el-table-column>
          <el-table-column prop="name" :label="t('名称')" min-width="160" />
          <el-table-column prop="description" :label="t('描述')" min-width="220" />
        </el-table>
      </div>

      <div class="panel-card stack">
        <h2>{{ t('操作') }}</h2>
        <el-table v-loading="loading" :data="actions" border>
          <el-table-column label="Key" min-width="180">
            <template #default="{ row }">
              {{ gmId(row.key) }}
            </template>
          </el-table-column>
          <el-table-column prop="name" :label="t('名称')" min-width="160" />
          <el-table-column prop="risk" :label="t('风险')" width="110" />
        </el-table>
      </div>
    </div>

    <div class="two-column-grid">
      <div class="panel-card stack">
        <h2>{{ t('菜单') }}</h2>
        <el-table v-loading="loading" :data="menus" border>
          <el-table-column prop="id" label="ID" min-width="160" />
          <el-table-column prop="title" :label="t('标题')" min-width="160" />
          <el-table-column prop="route" :label="t('路由')" min-width="180" />
          <el-table-column prop="order" :label="t('顺序')" width="90" />
        </el-table>
      </div>

      <div class="panel-card stack">
        <h2>{{ t('路由') }}</h2>
        <el-table v-loading="loading" :data="routes" border>
          <el-table-column prop="id" label="ID" min-width="160" />
          <el-table-column prop="path" :label="t('路径')" min-width="160" />
          <el-table-column prop="component" :label="t('组件')" min-width="200" />
        </el-table>
      </div>
    </div>
  </section>
</template>
