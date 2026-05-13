<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useGmStore } from '@/stores/gm'

const gm = useGmStore()
const { t } = useI18n()
</script>

<template>
  <section class="section-grid">
    <article class="panel-card metric">
      <span>{{ t('集群') }}</span>
      <strong>{{ gm.activeCluster }}</strong>
    </article>
    <article class="panel-card metric">
      <span>{{ t('操作人') }}</span>
      <strong>{{ gm.operator }}</strong>
    </article>
    <article class="panel-card metric">
      <span>{{ t('主入口') }}</span>
      <strong>GM API</strong>
    </article>
  </section>

  <section class="panel-card dashboard-note">
    <div>
      <p>{{ t('下一步') }}</p>
      <h2>{{ t('把 GM 能力拆成独立模块') }}</h2>
    </div>
    <el-space wrap>
      <el-tag>{{ t('脚本任务') }}</el-tag>
      <el-tag type="success">{{ t('配置 reload') }}</el-tag>
      <el-tag type="warning">{{ t('运行补丁') }}</el-tag>
      <el-tag type="info">{{ t('集群状态') }}</el-tag>
    </el-space>
  </section>
</template>

<style scoped>
.dashboard-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 16px;
}

.dashboard-note p,
.dashboard-note h2 {
  margin: 0;
}

.dashboard-note p {
  color: var(--gm-muted);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.dashboard-note h2 {
  margin-top: 6px;
  font-size: 18px;
  letter-spacing: 0;
}

@media (max-width: 760px) {
  .dashboard-note {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
