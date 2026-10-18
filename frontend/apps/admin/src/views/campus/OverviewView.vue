<template>
  <section class="page">
    <div class="page-heading">
      <div>
        <h1>学校范围</h1>
        <p>运营范围来自服务端授权，与平台租户和学生当前浏览学校相互独立。</p>
      </div>
      <el-button :loading="scope.status === 'loading'" @click="scope.loadContext()">
        重新检查
      </el-button>
    </div>

    <el-alert
      v-if="scope.status !== 'ready'"
      title="学校权限上下文接口未就绪"
      :description="scope.errorMessage || campusContextCapability.reason"
      type="warning"
      :closable="false"
      show-icon
    />

    <div class="summary">
      <el-card shadow="never">
        <span class="muted">授权学校</span>
        <strong>{{ scope.status === 'ready' ? scope.schools.length : '待加载' }}</strong>
        <p>只允许选择服务端返回的范围；未知学校默认拒绝。</p>
      </el-card>
      <el-card shadow="never">
        <span class="muted">授权版本</span>
        <strong>{{ scope.authorizationVersion ?? '—' }}</strong>
        <p>授权撤销或变更后必须重新读取上下文。</p>
      </el-card>
      <el-card shadow="never">
        <span class="muted">选择代次</span>
        <strong>{{ scope.generation }}</strong>
        <p>列表和详情须绑定此代次；切校后晚到响应不会回填。</p>
      </el-card>
    </div>

    <el-card shadow="never" class="scope-card">
      <template #header><strong>当前可用范围</strong></template>
      <el-table v-if="scope.status === 'ready'" :data="scope.schools" row-key="schoolId">
        <el-table-column prop="name" label="学校" min-width="180" />
        <el-table-column prop="schoolId" label="学校 ID" min-width="140" />
        <el-table-column label="校区" min-width="220">
          <template #default="slot">
            {{ slot.row.campuses.map((campus: { name: string }) => campus.name).join('、') || '无' }}
          </template>
        </el-table-column>
        <el-table-column label="认证审核" min-width="130">
          <template #default="slot">
            <el-tag
              v-if="
                slot.row.actions.includes('VERIFICATION_REVIEW') ||
                slot.row.campuses.some((campus: { actions: string[] }) =>
                  campus.actions.includes('VERIFICATION_REVIEW')
                )
              "
              type="success"
            >
              全校或部分校区
            </el-tag>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="未加载学校数据；不会使用演示授权范围。" />
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { campusContextCapability } from '@/api/campus/context'
import { useCampusScopeStore } from '@/stores/campusScope'

const scope = useCampusScopeStore()
</script>

<style scoped>
.summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.summary strong {
  display: block;
  margin-top: 10px;
  font-size: 26px;
}

.summary p {
  margin: 9px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.scope-card {
  margin-top: 16px;
}

@media (width <= 900px) {
  .summary {
    grid-template-columns: 1fr;
  }
}
</style>
