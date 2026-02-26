<script setup lang="ts">
import { ref } from 'vue'
import SessionList from '@/components/admin/SessionList.vue'
import UserManagement from '@/components/admin/UserManagement.vue'
import CategoryListPage from '@/views/admin/categories/CategoryListPage.vue'
import ScriptListPage from '@/views/admin/scripts/ScriptListPage.vue'
import TemplateList from '@/views/admin/templates/TemplateList.vue'

type AdminTab = 'sessions' | 'users' | 'categories' | 'scripts' | 'templates'

const activeTab = ref<AdminTab>('sessions')
</script>

<template>
  <div class="container mx-auto px-6 py-8 max-w-7xl">
    <div class="mb-8">
      <h1 class="text-3xl font-bold text-slate-100 mb-2">Admin Panel</h1>
      <p class="text-slate-400">Manage sessions, users, and vulnerability taxonomy</p>
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
      <button
        @click="activeTab = 'categories'"
        :class="[
          'px-4 py-2 text-sm font-medium transition-colors',
          activeTab === 'categories'
            ? 'text-primary-500 border-b-2 border-primary-500'
            : 'text-slate-400 hover:text-slate-200'
        ]"
      >
        Vulnerability Categories
      </button>
      <button
        @click="activeTab = 'templates'"
        :class="[
          'px-4 py-2 text-sm font-medium transition-colors',
          activeTab === 'templates'
            ? 'text-primary-500 border-b-2 border-primary-500'
            : 'text-slate-400 hover:text-slate-200'
        ]"
      >
        Templates
      </button>
      <button
        @click="activeTab = 'scripts'"
        :class="[
          'px-4 py-2 text-sm font-medium transition-colors',
          activeTab === 'scripts'
            ? 'text-primary-500 border-b-2 border-primary-500'
            : 'text-slate-400 hover:text-slate-200'
        ]"
      >
        Vulnerability Scripts
      </button>
    </div>

    <!-- Tab Content -->
    <div>
      <SessionList v-if="activeTab === 'sessions'" />
      <UserManagement v-else-if="activeTab === 'users'" />
      <CategoryListPage v-else-if="activeTab === 'categories'" />
      <TemplateList v-else-if="activeTab === 'templates'" />
      <ScriptListPage v-else />
    </div>
  </div>
</template>
