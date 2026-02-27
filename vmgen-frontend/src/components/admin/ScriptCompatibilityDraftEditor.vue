<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import type { ScriptListItem, ScriptSummary } from '@/types'

const props = defineProps<{
  scriptName: string
  allScripts: ScriptListItem[]
  conflictsWithScriptIds: string[]
  requiresScriptIds: string[]
}>()

const emit = defineEmits<{
  'update:conflictsWithScriptIds': [value: string[]]
  'update:requiresScriptIds': [value: string[]]
}>()

const selectedConflict = ref('')
const selectedDependency = ref('')

const conflictIdSet = computed(() => new Set(props.conflictsWithScriptIds ?? []))
const dependencyIdSet = computed(() => new Set(props.requiresScriptIds ?? []))

const canSelectConflict = (id: string) => !conflictIdSet.value.has(id) && !dependencyIdSet.value.has(id)
const canSelectDependency = (id: string) => !dependencyIdSet.value.has(id) && !conflictIdSet.value.has(id)

const conflictScripts = computed(() => {
  const ids = props.conflictsWithScriptIds ?? []
  const map = new Map(props.allScripts.map(s => [s.id, s]))
  return ids.map(id => map.get(id)).filter(Boolean) as ScriptListItem[]
})

const dependencyScripts = computed(() => {
  const ids = props.requiresScriptIds ?? []
  const map = new Map(props.allScripts.map(s => [s.id, s]))
  return ids.map(id => map.get(id)).filter(Boolean) as ScriptListItem[]
})

function addConflict() {
  const id = selectedConflict.value
  if (!id || !canSelectConflict(id)) return
  emit('update:conflictsWithScriptIds', [...(props.conflictsWithScriptIds ?? []), id])
  selectedConflict.value = ''
}

function removeConflict(id: string) {
  emit(
    'update:conflictsWithScriptIds',
    (props.conflictsWithScriptIds ?? []).filter(x => x !== id),
  )
}

function addDependency() {
  const id = selectedDependency.value
  if (!id || !canSelectDependency(id)) return
  emit('update:requiresScriptIds', [...(props.requiresScriptIds ?? []), id])
  selectedDependency.value = ''
}

function removeDependency(id: string) {
  emit(
    'update:requiresScriptIds',
    (props.requiresScriptIds ?? []).filter(x => x !== id),
  )
}
</script>

<template>
  <BaseCard class="space-y-4">
    <div>
      <h2 class="text-lg font-semibold text-slate-100">Compatibility</h2>
      <p class="text-xs text-slate-400">
        Prevent conflicting scripts from being selected together and express hard dependencies.
      </p>
    </div>

    <div class="flex flex-col gap-6">
      <div class="space-y-3">
        <h3 class="text-sm font-medium text-slate-200">Conflicts with</h3>
        <p class="text-xs text-slate-500">
          Scripts that must never be combined with <span class="font-semibold">{{ scriptName }}</span>.
        </p>

        <div class="space-y-2">
          <div
            v-for="s in conflictScripts"
            :key="s.id"
            class="flex items-center justify-between rounded border border-slate-700 bg-slate-900/70 px-3 py-2"
          >
            <div class="min-w-0">
              <p class="text-sm text-slate-100 truncate">{{ s.name }}</p>
              <p class="text-[11px] font-mono text-slate-500 truncate">
                {{ s.identifier }}@{{ s.version }}
              </p>
            </div>
            <BaseButton type="button" variant="ghost" size="sm" class="shrink-0" @click="removeConflict(s.id)">
              Remove
            </BaseButton>
          </div>

          <div class="flex items-stretch gap-2">
            <select
              v-model="selectedConflict"
              class="flex-1 min-w-0 h-10 rounded-md border border-slate-700 bg-slate-900 px-3 text-sm text-slate-100"
            >
              <option value="">Add conflict…</option>
              <option
                v-for="s in allScripts"
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
              class="h-10 shrink-0"
              :disabled="!selectedConflict || !canSelectConflict(selectedConflict)"
              @click="addConflict"
            >
              Add
            </BaseButton>
          </div>
        </div>
      </div>

      <div class="space-y-3 border-t border-slate-800 pt-4">
        <h3 class="text-sm font-medium text-slate-200">Depends on</h3>
        <p class="text-xs text-slate-500">
          Scripts that must be auto-added and run before <span class="font-semibold">{{ scriptName }}</span>.
        </p>

        <div class="space-y-2">
          <div
            v-for="s in dependencyScripts"
            :key="s.id"
            class="flex items-center justify-between rounded border border-slate-700 bg-slate-900/70 px-3 py-2"
          >
            <div class="min-w-0">
              <p class="text-sm text-slate-100 truncate">{{ s.name }}</p>
              <p class="text-[11px] font-mono text-slate-500 truncate">
                {{ s.identifier }}@{{ s.version }}
              </p>
            </div>
            <BaseButton type="button" variant="ghost" size="sm" class="shrink-0" @click="removeDependency(s.id)">
              Remove
            </BaseButton>
          </div>

          <div class="flex items-stretch gap-2">
            <select
              v-model="selectedDependency"
              class="flex-1 min-w-0 h-10 rounded-md border border-slate-700 bg-slate-900 px-3 text-sm text-slate-100"
            >
              <option value="">Add dependency…</option>
              <option
                v-for="s in allScripts"
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
              class="h-10 shrink-0"
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

