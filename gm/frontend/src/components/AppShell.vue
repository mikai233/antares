<script setup lang="ts">
import { DocumentChecked, Monitor, Operation, SetUp } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">GM</div>
        <div>
          <strong>Antares Console</strong>
          <span>Cluster operations</span>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        class="nav-menu"
        router
        @select="(path: string) => router.push(path)"
      >
        <el-menu-item index="/">
          <el-icon><Monitor /></el-icon>
          <span>控制台</span>
        </el-menu-item>
        <el-menu-item index="/scripts">
          <el-icon><Operation /></el-icon>
          <span>脚本执行</span>
        </el-menu-item>
        <el-menu-item index="/script-executions">
          <el-icon><DocumentChecked /></el-icon>
          <span>执行记录</span>
        </el-menu-item>
        <el-menu-item index="/nodes" disabled>
          <el-icon><SetUp /></el-icon>
          <span>节点管理</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="main-panel">
      <header class="topbar">
        <div>
          <p>GM 后台</p>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <el-tag effect="dark" type="success">Spring Boot API</el-tag>
      </header>

      <router-view />
    </main>
  </div>
</template>
