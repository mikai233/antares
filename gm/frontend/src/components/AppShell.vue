<script setup lang="ts">
import { Box, Coin, Connection, DocumentChecked, Files, Monitor, Moon, Operation, SetUp, Sunny, SwitchButton, Timer } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { backendHealth, checkBackendHealth } from '@/api/backendHealth'
import { toggleLocale } from '@/i18n'
import { loadServerTimeZone, serverTimeZoneText } from '@/utils/serverTime'

const route = useRoute()
const router = useRouter()
const { locale, t } = useI18n()
const theme = ref<'light' | 'dark'>('light')
const isDark = computed(() => theme.value === 'dark')
const isEnglish = computed(() => locale.value === 'en-US')
const pageTitle = computed(() => {
  const title = String(route.meta.title ?? '')
  return title ? t(title) : ''
})
const apiStatusType = computed(() => {
  if (backendHealth.status === 'online') {
    return 'success'
  }
  return backendHealth.status === 'offline' ? 'danger' : 'warning'
})
const apiStatusText = computed(() => {
  if (backendHealth.status === 'online') {
    return t('API 正常')
  }
  return backendHealth.status === 'offline' ? t('API 离线') : t('API 检查中')
})
let healthTimer: number | undefined
let themeSwitchFrame: number | undefined
let themeSwitchSecondFrame: number | undefined

function toggleTheme() {
  suppressThemeTransition()
  theme.value = isDark.value ? 'light' : 'dark'
}

function applyTheme(nextTheme: 'light' | 'dark') {
  document.documentElement.dataset.theme = nextTheme
  document.documentElement.classList.toggle('dark', nextTheme === 'dark')
  localStorage.setItem('gm-theme', nextTheme)
}

function suppressThemeTransition() {
  const root = document.documentElement
  root.classList.add('theme-switching')
  if (themeSwitchFrame != null) {
    window.cancelAnimationFrame(themeSwitchFrame)
  }
  if (themeSwitchSecondFrame != null) {
    window.cancelAnimationFrame(themeSwitchSecondFrame)
  }
  themeSwitchFrame = window.requestAnimationFrame(() => {
    themeSwitchSecondFrame = window.requestAnimationFrame(() => {
      root.classList.remove('theme-switching')
      themeSwitchFrame = undefined
      themeSwitchSecondFrame = undefined
    })
  })
}

watch(theme, applyTheme)

onMounted(() => {
  const savedTheme = localStorage.getItem('gm-theme')
  theme.value = savedTheme === 'dark' ? 'dark' : 'light'
  applyTheme(theme.value)
  void checkBackendHealth()
  void loadServerTimeZone()
  healthTimer = window.setInterval(() => {
    void checkBackendHealth()
  }, 10_000)
})

onBeforeUnmount(() => {
  if (healthTimer != null) {
    window.clearInterval(healthTimer)
  }
  if (themeSwitchFrame != null) {
    window.cancelAnimationFrame(themeSwitchFrame)
  }
  if (themeSwitchSecondFrame != null) {
    window.cancelAnimationFrame(themeSwitchSecondFrame)
  }
  document.documentElement.classList.remove('theme-switching')
})
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">GM</div>
        <div>
          <strong>Antares Console</strong>
          <span>{{ t('集群运维后台') }}</span>
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
          <span>{{ t('控制台') }}</span>
        </el-menu-item>
        <el-menu-item index="/scripts">
          <el-icon><Operation /></el-icon>
          <span>{{ t('脚本任务') }}</span>
        </el-menu-item>
        <el-menu-item index="/script-jobs">
          <el-icon><DocumentChecked /></el-icon>
          <span>{{ t('任务记录') }}</span>
        </el-menu-item>
        <el-menu-item index="/patches">
          <el-icon><Box /></el-icon>
          <span>{{ t('运行补丁') }}</span>
        </el-menu-item>
        <el-menu-item index="/config">
          <el-icon><Coin /></el-icon>
          <span>{{ t('配置中心') }}</span>
        </el-menu-item>
        <el-menu-item index="/cluster">
          <el-icon><Connection /></el-icon>
          <span>{{ t('集群管理') }}</span>
        </el-menu-item>
        <el-menu-item index="/game-time">
          <el-icon><Timer /></el-icon>
          <span>{{ t('游戏时间') }}</span>
        </el-menu-item>
        <el-menu-item index="/shutdown">
          <el-icon><SwitchButton /></el-icon>
          <span>{{ t('停服控制') }}</span>
        </el-menu-item>
        <el-menu-item index="/gm-metadata">
          <el-icon><Files /></el-icon>
          <span>{{ t('GM 元数据') }}</span>
        </el-menu-item>
        <el-menu-item index="/nodes" disabled>
          <el-icon><SetUp /></el-icon>
          <span>{{ t('节点管理') }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="main-panel">
      <header class="topbar">
        <div>
          <p>{{ t('GM 后台') }}</p>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-actions">
          <el-button @click="toggleLocale">
            {{ isEnglish ? t('中文') : t('English') }}
          </el-button>
          <el-button circle :aria-label="isDark ? t('切换亮色模式') : t('切换暗色模式')" @click="toggleTheme">
            <el-icon>
              <Sunny v-if="isDark" />
              <Moon v-else />
            </el-icon>
          </el-button>
          <el-tag effect="plain" :type="apiStatusType" :title="backendHealth.message">
            {{ apiStatusText }}
          </el-tag>
          <el-tag effect="plain" type="info">
            {{ t('服务器时区') }}: {{ serverTimeZoneText() }}
          </el-tag>
        </div>
      </header>

      <router-view />
    </main>
  </div>
</template>
