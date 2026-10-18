import { createApp } from 'vue'
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElStep,
  ElSteps,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip
} from 'element-plus'
import 'element-plus/dist/index.css'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App).use(createPinia()).use(router)

for (const component of [
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElStep,
  ElSteps,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip
]) {
  app.use(component)
}

app.mount('#app')
