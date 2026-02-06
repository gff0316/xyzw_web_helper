<template>
  <n-message-provider>
    <div class="app-shell">
      <router-view />
    </div>
  </n-message-provider>
</template>

<script setup>
import { NMessageProvider } from "naive-ui";
</script>

<style scoped>
.app-shell {
  min-height: 100dvh;
  color: #e2e8f0;
}
</style>
