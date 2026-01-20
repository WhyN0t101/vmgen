<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppNavbar from '@/components/layout/AppNavbar.vue'
</script>

<template>
  <div class="min-h-screen bg-slate-950">
    <AppNavbar />
    <main>
      <RouterView />
    </main>
  </div>
</template>

<style scoped></style>
