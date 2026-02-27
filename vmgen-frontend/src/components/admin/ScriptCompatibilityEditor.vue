<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import type { ScriptDetail, ScriptCompatibilityInfo, ScriptListItem } from '@/types'
import { api } from '@/api/endpoints'

const props = defineProps<{
  script: ScriptDetail
  allScripts: ScriptListItem[]
  compatibility: ScriptCompatibilityInfo | null
}>()

const emit = defineEmits<{
  refresh: []
}>()

const selectedConflict = ref('')
const selectedDependency = ref('')

const otherScripts = computed(() =>
  props.allScripts.filter(s => s.id !== props.script.id),
)

const conflictIds = computed(() => new Set(props.compatibility?.conflictsWith.map(s => s.id) ?? []))
const dependencyIds = computed(() => new Set(props.compatibility?.requires.map(s => s.id) ?? []))

// Ensure we never show duplicate entries even if the backend returns them
const uniqueConflicts = computed(() => {
  const seen = new Set<string>()
  return (props.compatibility?.conflictsWith ?? []).filter(s => {
    if (seen.has(s.id)) return false
    seen.add(s.id)
    return true
  })
})

const uniqueDependencies = computed(() => {
  const seen = new Set<string>()
  return (props.compatibility?.requires ?? []).filter(s => {
    if (seen.has(s.id)) return false
    seen.add(s.id)
    return true
  })
})

// A script cannot be both a conflict and a dependency at the same time
const canSelectConflict = (id: string) =>
  !conflictIds.value.has(id) && !dependencyIds.value.has(id)

const canSelectDependency = (id: string) =>
  !dependencyIds.value.has(id) && !conflictIds.value.has(id)

async function addConflict() {
  if (!selectedConflict.value || !canSelectConflict(selectedConflict.value)) return
  try {
    await api.admin.addScriptConflict(props.script.id, selectedConflict.value)
  } catch (err: any) {
    const msg = err?.response?.data?.message || 'Failed to add conflict'
    alert(msg)
  } finally {
    selectedConflict.value = ''
    emit('refresh')
  }
}

async function removeConflict(id: string) {
  await api.admin.removeScriptConflict(props.script.id, id)
  emit('refresh')
}

async function addDependency() {
  if (!selectedDependency.value || !canSelectDependency(selectedDependency.value)) return
  try {
    await api.admin.addScriptDependency(props.script.id, selectedDependency.value)
  } catch (err: any) {
    const msg = err?.response?.data?.message || 'Failed to add dependency'
    alert(msg)
  } finally {
    selectedDependency.value = ''
    emit('refresh')
  }
}

async function removeDependency(id: string) {
  await api.admin.removeScriptDependency(props.script.id, id)
  emit('refresh')
}
</script>

<template>
  <BaseCard class="space-y-4">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold text-slate-100">Compatibility</h2>
        <p class="text-xs text-slate-400">
          Prevent conflicting scripts from being selected together and express hard dependencies.
        </p>
      </div>
    </div>

    <!-- Stack sections vertically to avoid overlapping controls across columns -->
    <div class="flex flex-col gap-6">
      <!-- Conflicts -->
      <div class="space-y-3">
        <h3 class="text-sm font-medium text-slate-200">Conflicts with</h3>
        <p class="text-xs text-slate-500">
          Scripts that must never be combined with <span class="font-semibold">{{ script.name }}</span>.
        </p>

        <div class="space-y-2">
          <div
            v-for="s in uniqueConflicts"
            :key="s.id"
            class="flex items-center justify-between rounded border border-slate-700 bg-slate-900/70 px-3 py-2"
          >
            <div>
              <p class="text-sm text-slate-100">{{ s.name }}</p>
              <p class="text-[11px] font-mono text-slate-500">
                {{ s.identifier }}@{{ s.version }}
              </p>
            </div>
            <BaseButton type="button" variant="ghost" size="sm" @click="removeConflict(s.id)">
              Remove
            </BaseButton>
          </div>

          <div class="flex items-stretch gap-2">
            <select
              v-model="selectedConflict"
              class="flex-1 h-10 rounded-md border border-slate-700 bg-slate-900 px-3 text-sm text-slate-100"
            >
              <option value="">Add conflict…</option>
              <option
                v-for="s in otherScripts"
                :key="s.id"
                :value="s.id"
                :disabled="!canSelectConflict(s.id)"
              >
                {{ s.name }} ({{ s.identifier }}@{{ s.version }})
              </option>
            </select>
            <BaseButton
              type="button"
              size="sm"
              class="h-10"
              :disabled="!selectedConflict || !canSelectConflict(selectedConflict)"
              @click="addConflict"
            >
              Add
            </BaseButton>
          </div>
        </div>
      </div>

      <!-- Dependencies -->
      <div class="space-y-3 border-t border-slate-800 pt-4">
        <h3 class="text-sm font-medium text-slate-200">Depends on</h3>
        <p class="text-xs text-slate-500">
          Scripts that must be auto-added and run before <span class="font-semibold">{{ script.name }}</span>.
        </p>

        <div class="space-y-2">
          <div
            v-for="s in uniqueDependencies"
            :key="s.id"
            class="flex items-center justify-between rounded border border-slate-700 bg-slate-900/70 px-3 py-2"
          >
            <div>
              <p class="text-sm text-slate-100">{{ s.name }}</p>
              <p class="text-[11px] font-mono text-slate-500">
                {{ s.identifier }}@{{ s.version }}
              </p>
            </div>
            <BaseButton type="button" variant="ghost" size="sm" @click="removeDependency(s.id)">
              Remove
            </BaseButton>
          </div>

          <div class="flex items-stretch gap-2">
            <select
              v-model="selectedDependency"
              class="flex-1 h-10 rounded-md border border-slate-700 bg-slate-900 px-3 text-sm text-slate-100"
            >
              <option value="">Add dependency…</option>
              <option
                v-for="s in otherScripts"
                :key="s.id"
                :value="s.id"
                :disabled="!canSelectDependency(s.id)"
              >
                {{ s.name }} ({{ s.identifier }}@{{ s.version }})
              </option>
            </select>
            <BaseButton
              type="button"
              size="sm"
              class="h-10"
              :disabled="!selectedDependency || !canSelectDependency(selectedDependency)"
              @click="addDependency"
            >
              Add
            </BaseButton>
          </div>
        </div>
      </div>
    </div>
  </BaseCard>
</template>

