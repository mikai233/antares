<script setup lang="ts">
import { elementLocale } from '@/i18n'
import AppShell from '@/components/AppShell.vue'
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <AppShell />
  </el-config-provider>
</template>
