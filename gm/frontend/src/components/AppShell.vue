<script setup lang="ts">
import { Box, Coin, Connection, DocumentChecked, Files, Monitor, Moon, Operation, SetUp, Sunny, SwitchButton, Timer } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const theme = ref<'light' | 'dark'>('light')
const isDark = computed(() => theme.value === 'dark')

function toggleTheme() {
  theme.value = isDark.value ? 'light' : 'dark'
}

function applyTheme(nextTheme: 'light' | 'dark') {
  document.documentElement.dataset.theme = nextTheme
  document.documentElement.classList.toggle('dark', nextTheme === 'dark')
  localStorage.setItem('gm-theme', nextTheme)
}

watch(theme, applyTheme)

onMounted(() => {
  const savedTheme = localStorage.getItem('gm-theme')
  theme.value = savedTheme === 'dark' ? 'dark' : 'light'
  applyTheme(theme.value)
})
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
          <span>脚本任务</span>
        </el-menu-item>
        <el-menu-item index="/script-jobs">
          <el-icon><DocumentChecked /></el-icon>
          <span>任务记录</span>
        </el-menu-item>
        <el-menu-item index="/patches">
          <el-icon><Box /></el-icon>
          <span>运行补丁</span>
        </el-menu-item>
        <el-menu-item index="/config">
          <el-icon><Coin /></el-icon>
          <span>配置中心</span>
        </el-menu-item>
        <el-menu-item index="/cluster">
          <el-icon><Connection /></el-icon>
          <span>集群管理</span>
        </el-menu-item>
        <el-menu-item index="/game-time">
          <el-icon><Timer /></el-icon>
          <span>游戏时间</span>
        </el-menu-item>
        <el-menu-item index="/shutdown">
          <el-icon><SwitchButton /></el-icon>
          <span>停服控制</span>
        </el-menu-item>
        <el-menu-item index="/gm-metadata">
          <el-icon><Files /></el-icon>
          <span>GM 元数据</span>
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
        <div class="topbar-actions">
          <el-button circle :aria-label="isDark ? '切换亮色模式' : '切换暗色模式'" @click="toggleTheme">
            <el-icon>
              <Sunny v-if="isDark" />
              <Moon v-else />
            </el-icon>
          </el-button>
          <el-tag effect="plain" type="success">Spring Boot API</el-tag>
        </div>
      </header>

      <router-view />
    </main>
  </div>
</template>
