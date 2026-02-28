<script setup lang="ts">
import type { ScriptParameter } from '@/types'

interface ScriptWithParams {
  id: string
  name: string
  params: ScriptParameter[]
}

interface Props {
  scripts: ScriptWithParams[]
  modelValue: Record<string, Record<string, string>>
  randomizeFlags?: Record<string, Record<string, boolean>>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, Record<string, string>>]
  'update:randomizeFlags': [value: Record<string, Record<string, boolean>>]
}>()

function getValue(scriptId: string, paramName: string): string {
  return props.modelValue[scriptId]?.[paramName] ?? ''
}

function setValue(scriptId: string, paramName: string, value: string) {
  const next = { ...props.modelValue }
  if (!next[scriptId]) next[scriptId] = {}
  next[scriptId] = { ...next[scriptId], [paramName]: value }
  emit('update:modelValue', next)
}

function isRandomized(scriptId: string, paramName: string): boolean {
  return props.randomizeFlags?.[scriptId]?.[paramName] ?? false
}

function toggleRandomize(scriptId: string, paramName: string) {
  const next = { ...(props.randomizeFlags ?? {}) }
  if (!next[scriptId]) next[scriptId] = {}
  next[scriptId] = { ...next[scriptId], [paramName]: !isRandomized(scriptId, paramName) }
  emit('update:randomizeFlags', next)
}
</script>

<template>
  <div v-if="scripts.some(s => s.params.length > 0)" class="mt-4 space-y-4">
    <div class="flex items-center gap-2 mb-2">
      <h2 class="text-lg font-semibold text-slate-100">Script Parameters</h2>
      <span class="text-xs text-slate-500">Fill in values required by the selected scripts</span>
    </div>

    <div v-for="script in scripts" :key="script.id">
      <div v-if="script.params.length > 0" class="rounded-xl border border-slate-700/60 bg-slate-800/40 p-4 space-y-3">
        <p class="text-xs font-mono tracking-widest uppercase text-slate-400">
          {{ script.name }}
        </p>

        <div
          v-for="param in script.params"
          :key="param.parameterName"
          class="space-y-1.5"
        >
          <!-- Label row -->
          <div class="flex items-center justify-between">
            <label class="text-sm font-medium text-slate-200">
              {{ param.displayName }}
              <span
                v-if="param.isRequired && param.valueSource === 'USER_INPUT'"
                class="text-red-400 ml-0.5"
                title="Required"
              >*</span>
            </label>

            <!-- Randomize toggle for USER_INPUT_OR_RANDOM -->
            <label
              v-if="param.valueSource === 'USER_INPUT_OR_RANDOM'"
              class="flex items-center gap-1.5 cursor-pointer select-none"
            >
              <input
                type="checkbox"
                :checked="isRandomized(script.id, param.parameterName)"
                @change="toggleRandomize(script.id, param.parameterName)"
                class="w-3.5 h-3.5 rounded border-slate-600 bg-slate-700 text-primary-500 focus:ring-primary-500"
              />
              <span class="text-xs text-slate-400">Auto-generate</span>
            </label>
          </div>

          <p v-if="param.description" class="text-xs text-slate-500">
            {{ param.description }}
          </p>

          <!-- Auto-generated notice -->
          <div
            v-if="isRandomized(script.id, param.parameterName)"
            class="text-xs text-slate-500 italic px-1"
          >
            Will be generated automatically by the worker.
          </div>

          <!-- CHOICE select -->
          <select
            v-else-if="param.parameterType === 'CHOICE' && param.choices?.length"
            :value="getValue(script.id, param.parameterName)"
            @change="setValue(script.id, param.parameterName, ($event.target as HTMLSelectElement).value)"
            class="w-full bg-slate-900 border border-slate-700 rounded-md px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-primary-500"
          >
            <option value="" disabled>Select…</option>
            <option
              v-for="choice in param.choices"
              :key="choice.id"
              :value="choice.value"
            >
              {{ choice.label }}
            </option>
          </select>

          <!-- BOOLEAN checkbox -->
          <div
            v-else-if="param.parameterType === 'BOOLEAN'"
            class="flex items-center gap-2"
          >
            <input
              type="checkbox"
              :checked="getValue(script.id, param.parameterName) === 'true'"
              @change="setValue(script.id, param.parameterName, String(($event.target as HTMLInputElement).checked))"
              class="w-4 h-4 rounded border-slate-700 bg-slate-800 text-primary-500 focus:ring-primary-500"
            />
            <span class="text-xs text-slate-400">
              {{ getValue(script.id, param.parameterName) === 'true' ? 'Enabled' : 'Disabled' }}
            </span>
          </div>

          <!-- INTEGER input -->
          <input
            v-else-if="param.parameterType === 'INTEGER'"
            type="number"
            :value="getValue(script.id, param.parameterName)"
            :placeholder="param.defaultValue ?? '0'"
            @input="setValue(script.id, param.parameterName, ($event.target as HTMLInputElement).value)"
            class="w-full bg-slate-900 border border-slate-700 rounded-md px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-primary-500 placeholder-slate-600"
          />

          <!-- STRING / PATH text input -->
          <input
            v-else
            type="text"
            :value="getValue(script.id, param.parameterName)"
            :placeholder="param.defaultValue ?? param.displayName"
            @input="setValue(script.id, param.parameterName, ($event.target as HTMLInputElement).value)"
            class="w-full bg-slate-900 border border-slate-700 rounded-md px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-primary-500 placeholder-slate-600"
          />
        </div>
      </div>
    </div>
  </div>
</template>
