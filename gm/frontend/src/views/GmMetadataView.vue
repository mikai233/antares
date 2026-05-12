<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
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
    ElMessage.error(error instanceof Error ? error.message : '加载 GM 元数据失败')
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
        <h2>能力元数据</h2>
      </div>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </div>

    <div class="two-column-grid">
      <div class="panel-card stack">
        <h2>Features</h2>
        <el-table v-loading="loading" :data="features" border>
          <el-table-column label="ID" min-width="160">
            <template #default="{ row }">
              {{ gmId(row.id) }}
            </template>
          </el-table-column>
          <el-table-column prop="name" label="Name" min-width="160" />
          <el-table-column prop="description" label="Description" min-width="220" />
        </el-table>
      </div>

      <div class="panel-card stack">
        <h2>Actions</h2>
        <el-table v-loading="loading" :data="actions" border>
          <el-table-column label="Key" min-width="180">
            <template #default="{ row }">
              {{ gmId(row.key) }}
            </template>
          </el-table-column>
          <el-table-column prop="name" label="Name" min-width="160" />
          <el-table-column prop="risk" label="Risk" width="110" />
        </el-table>
      </div>
    </div>

    <div class="two-column-grid">
      <div class="panel-card stack">
        <h2>Menus</h2>
        <el-table v-loading="loading" :data="menus" border>
          <el-table-column prop="id" label="ID" min-width="160" />
          <el-table-column prop="title" label="Title" min-width="160" />
          <el-table-column prop="route" label="Route" min-width="180" />
          <el-table-column prop="order" label="Order" width="90" />
        </el-table>
      </div>

      <div class="panel-card stack">
        <h2>Routes</h2>
        <el-table v-loading="loading" :data="routes" border>
          <el-table-column prop="id" label="ID" min-width="160" />
          <el-table-column prop="path" label="Path" min-width="160" />
          <el-table-column prop="component" label="Component" min-width="200" />
        </el-table>
      </div>
    </div>
  </section>
</template>
