<template>
  <main class="status-page">
    <strong>{{ status }}</strong>
    <h1>{{ title }}</h1>
    <router-link to="/campus/overview">返回运营台</router-link>
  </main>
</template>

<script setup lang="ts">
defineProps<{ status: string; title: string }>()
</script>

<style scoped>
.status-page {
  display: grid;
  min-height: 100vh;
  place-content: center;
  text-align: center;
}

strong {
  color: #217a55;
  font-size: 64px;
}

a {
  color: #217a55;
}
</style>
