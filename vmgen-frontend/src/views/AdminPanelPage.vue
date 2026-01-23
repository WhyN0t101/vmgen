<script setup lang="ts">
import { ref } from 'vue'
import SessionList from '@/components/admin/SessionList.vue'
import UserManagement from '@/components/admin/UserManagement.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

const activeTab = ref<'sessions' | 'users'>('sessions')
</script>

<template>
  <div class="container mx-auto px-6 py-8 max-w-7xl">
    <div class="mb-8">
      <h1 class="text-3xl font-bold text-slate-100 mb-2">Admin Panel</h1>
      <p class="text-slate-400">Manage sessions and user roles</p>
    </div>

    <!-- Tabs -->
    <div class="flex gap-2 mb-6 border-b border-slate-800">
      <button
        @click="activeTab = 'sessions'"
        :class="[
          'px-4 py-2 text-sm font-medium transition-colors',
          activeTab === 'sessions'
            ? 'text-primary-500 border-b-2 border-primary-500'
            : 'text-slate-400 hover:text-slate-200'
        ]"
      >
        Sessions
      </button>
      <button
        @click="activeTab = 'users'"
        :class="[
          'px-4 py-2 text-sm font-medium transition-colors',
          activeTab === 'users'
            ? 'text-primary-500 border-b-2 border-primary-500'
            : 'text-slate-400 hover:text-slate-200'
        ]"
      >
        User Management
      </button>
    </div>

    <!-- Tab Content -->
    <div>
      <SessionList v-if="activeTab === 'sessions'" />
      <UserManagement v-else />
    </div>
  </div>
</template>
