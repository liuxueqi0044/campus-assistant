<template>
  <div class="school-scope">
    <School class="icon" />
    <el-select
      v-if="scope.status === 'ready'"
      :model-value="scope.selectedSchoolId"
      aria-label="当前授权学校"
      @change="select"
    >
      <el-option
        v-for="school in scope.schools"
        :key="school.schoolId"
        :label="school.name"
        :value="school.schoolId"
      />
    </el-select>
    <el-select
      v-if="scope.status === 'ready'"
      :model-value="scope.selectedCampusId"
      aria-label="当前授权校区"
      @change="selectCampus"
    >
      <el-option label="全校范围" value="" />
      <el-option
        v-for="campus in scope.selectedSchool?.campuses || []"
        :key="campus.campusId"
        :label="campus.name"
        :value="campus.campusId"
      />
    </el-select>
    <el-tooltip v-else :content="scope.errorMessage || '学校范围尚未加载'" placement="bottom">
      <el-tag effect="plain" type="warning">学校范围未接入</el-tag>
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { School } from '@element-plus/icons-vue'
import { useCampusScopeStore } from '@/stores/campusScope'

const scope = useCampusScopeStore()

function select(value: string | number | boolean | undefined) {
  if (typeof value === 'string') scope.selectSchool(value)
}

function selectCampus(value: string | number | boolean | undefined) {
  if (typeof value === 'string') scope.selectCampus(value)
}

onMounted(() => {
  if (scope.status === 'idle') void scope.loadContext()
})
</script>

<style scoped>
.school-scope {
  display: flex;
  min-width: 420px;
  align-items: center;
  gap: 8px;
}

.icon {
  width: 18px;
  color: #217a55;
}

.el-select {
  width: 210px;
}
</style>
