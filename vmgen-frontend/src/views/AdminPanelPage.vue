<script setup lang="ts">
import { ref } from 'vue'
import SessionList from '@/components/admin/SessionList.vue'
import UserManagement from '@/components/admin/UserManagement.vue'
import CategoryListPage from '@/views/admin/categories/CategoryListPage.vue'
import ScriptListPage from '@/views/admin/scripts/ScriptListPage.vue'
import TemplateList from '@/views/admin/templates/TemplateList.vue'
import AdminBuildPanel from '@/views/admin/builds/AdminBuildPanel.vue'

type AdminTab = 'sessions' | 'users' | 'categories' | 'scripts' | 'templates' | 'builds'

const activeTab = ref<AdminTab>('sessions')
</script>

<template>
  <div class="max-w-7xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <header class="space-y-2 border-b border-slate-800/70 pb-4">
        <p class="text-[11px] font-mono tracking-[0.3em] uppercase text-slate-500">
          Vmgen // Admin Console
        </p>
        <div class="flex flex-wrap items-baseline justify-between gap-3">
          <div>
            <h1 class="text-3xl font-semibold text-slate-50">Control Surface</h1>
            <p class="text-sm text-slate-400">
              Sessions, access control, templates, and the vulnerability taxonomy.
            </p>
          </div>
        </div>
      </header>

      <!-- Tabs -->
      <nav class="flex flex-wrap gap-2 border-b border-slate-800/80 pb-2">
        <button
          @click="activeTab = 'sessions'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'sessions'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Sessions
        </button>
        <button
          @click="activeTab = 'users'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'users'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          User Management
        </button>
        <button
          @click="activeTab = 'categories'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'categories'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Vulnerability Categories
        </button>
        <button
          @click="activeTab = 'templates'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'templates'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Templates
        </button>
        <button
          @click="activeTab = 'scripts'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'scripts'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Vulnerability Scripts
        </button>
        <button
          @click="activeTab = 'builds'"
          :class="[
            'px-4 py-2 text-xs md:text-sm font-medium rounded-full transition-all duration-150',
            activeTab === 'builds'
              ? 'bg-sky-500/15 text-sky-300 border border-sky-500/50 shadow-[0_0_0_1px_rgba(8,145,178,0.35)]'
              : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/70 border border-transparent'
          ]"
        >
          Builds
        </button>
      </nav>

      <!-- Tab Content -->
      <section class="pt-2">
        <SessionList v-if="activeTab === 'sessions'" />
        <UserManagement v-else-if="activeTab === 'users'" />
        <CategoryListPage v-else-if="activeTab === 'categories'" />
        <TemplateList v-else-if="activeTab === 'templates'" />
        <AdminBuildPanel v-else-if="activeTab === 'builds'" />
        <ScriptListPage v-else />
      </section>
    </div>
  </div>
</template>
