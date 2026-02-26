<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { useCategoryList } from '@/composables/useCategories'
import { useScriptList, useDeprecateScript } from '@/composables/useScripts'

const router = useRouter()

const selectedCategoryId = ref<string | ''>('')
const selectedDifficulty = ref<string | ''>('')
const activeOnly = ref(true)

const { data: categoriesData } = useCategoryList(true)
const categories = computed(() => categoriesData.value ?? [])

const { data: scriptsData, refetch, isLoading } = useScriptList({
  categoryId: selectedCategoryId,
  difficulty: selectedDifficulty,
  activeOnly,
})

const scripts = computed(() => scriptsData.value ?? [])

const { mutateAsync: deprecateScript, isPending: isDeprecating } = useDeprecateScript()

async function handleDeprecate(id: string) {
  if (!confirm('Deprecate this script? It will no longer be available for new builds.')) return
  await deprecateScript(id)
  refetch()
}

function difficultyBadgeClass(level: string) {
  const v = level?.toLowerCase()
  if (v === 'easy') return 'bg-emerald-500/20 text-emerald-300'
  if (v === 'hard') return 'bg-rose-500/20 text-rose-300'
  return 'bg-amber-500/20 text-amber-300'
}
</script>

<template>
  <div class="container mx-auto px-6 py-8 max-w-6xl">
    <div class="mb-6 flex items-center justify-between gap-4">
      <div>
        <h1 class="text-3xl font-bold text-slate-100">Vulnerability Scripts</h1>
        <p class="text-sm text-slate-400">
          Manage vulnerability injection scripts and assign them to categories.
        </p>
      </div>
      <BaseButton variant="primary" @click="router.push({ name: 'admin-script-create' })">
        New Script
      </BaseButton>
    </div>

    <BaseCard class="mb-4">
      <div class="flex flex-wrap items-end gap-4">
        <div class="flex flex-col">
          <label class="mb-1 text-xs font-medium text-slate-400">Category</label>
          <select
            v-model="selectedCategoryId"
            class="min-w-[180px] rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
            @change="refetch()"
          >
            <option value="">All</option>
            <option v-for="cat in categories" :key="cat.id" :value="cat.id">
              {{ cat.name }}{{ cat.owaspMapping ? ` (${cat.owaspMapping})` : '' }}
            </option>
          </select>
        </div>
        <div class="flex flex-col">
          <label class="mb-1 text-xs font-medium text-slate-400">Difficulty</label>
          <select
            v-model="selectedDifficulty"
            class="min-w-[140px] rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
            @change="refetch()"
          >
            <option value="">All</option>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </div>
        <label class="flex items-center gap-2 text-xs text-slate-400">
          <input v-model="activeOnly" type="checkbox" class="rounded border-slate-600 bg-slate-900" @change="refetch()" />
          Active only
        </label>
      </div>
    </BaseCard>

    <div v-if="isLoading" class="space-y-3">
      <BaseCard v-for="i in 3" :key="i" class="h-24 animate-pulse bg-slate-800/60" />
    </div>

    <div v-else-if="scripts.length > 0" class="space-y-3">
      <BaseCard
        v-for="script in scripts"
        :key="script.id"
        padding="md"
        class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between"
      >
        <div class="flex-1">
          <div class="mb-1 flex flex-wrap items-center gap-2">
            <h2 class="text-base font-semibold text-slate-100">
              {{ script.name }}
            </h2>
            <code class="rounded bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300">
              {{ script.identifier }}@{{ script.version }}
            </code>
          </div>
          <div class="mb-2 flex flex-wrap items-center gap-2">
            <span
              class="rounded px-2 py-0.5 text-[11px] font-medium text-slate-900"
              :style="{ backgroundColor: script.categoryColor || '#cbd5f5' }"
            >
              {{ script.categoryName || 'Uncategorized' }}
            </span>
            <span
              class="rounded px-2 py-0.5 text-[11px] font-medium"
              :class="difficultyBadgeClass(script.difficultyLevel)"
            >
              {{ script.difficultyLevel }}
            </span>
            <span
              v-if="!script.isActive"
              class="rounded bg-slate-800 px-2 py-0.5 text-[11px] font-medium text-slate-300"
            >
              Deprecated
            </span>
            <span
              v-if="script.owaspMapping"
              class="rounded bg-slate-900 px-2 py-0.5 text-[11px] text-slate-400"
            >
              {{ script.owaspMapping }}
            </span>
          </div>
          <p class="text-xs text-slate-400">
            Created {{ new Date(script.createdAt).toLocaleString() }}
          </p>
        </div>

        <div class="flex items-center gap-2">
          <BaseButton
            variant="secondary"
            size="sm"
            @click="router.push({ name: 'admin-script-edit', params: { id: script.id } })"
          >
            Edit
          </BaseButton>
          <BaseButton
            v-if="script.isActive"
            variant="ghost"
            size="sm"
            :disabled="isDeprecating.value"
            @click="handleDeprecate(script.id)"
          >
            Deprecate
          </BaseButton>
        </div>
      </BaseCard>
    </div>

    <div v-else class="py-16 text-center text-slate-400">
      <p>No scripts defined yet.</p>
      <p class="mt-1 text-sm">Create your first vulnerability script to make templates more expressive.</p>
    </div>
  </div>
</template>

