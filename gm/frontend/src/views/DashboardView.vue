<script setup lang="ts">
import { useGmStore } from '@/stores/gm'

const gm = useGmStore()
</script>

<template>
  <section class="section-grid">
    <article class="panel-card metric">
      <span>Cluster</span>
      <strong>{{ gm.activeCluster }}</strong>
    </article>
    <article class="panel-card metric">
      <span>Operator</span>
      <strong>{{ gm.operator }}</strong>
    </article>
    <article class="panel-card metric">
      <span>Primary Flow</span>
      <strong>Script</strong>
    </article>
  </section>

  <section class="panel-card dashboard-note">
    <div>
      <p>Next</p>
      <h2>把 GM 能力拆成独立模块</h2>
    </div>
    <el-space wrap>
      <el-tag>脚本执行</el-tag>
      <el-tag type="success">节点状态</el-tag>
      <el-tag type="warning">操作审计</el-tag>
      <el-tag type="info">权限接入</el-tag>
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
  font-size: 28px;
  letter-spacing: -0.04em;
}

@media (max-width: 760px) {
  .dashboard-note {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
