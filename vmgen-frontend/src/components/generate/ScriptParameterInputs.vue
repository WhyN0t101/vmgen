<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
  'update:hasErrors': [value: boolean]
}>()

// Track per-field validation error messages.
const errors = ref<Record<string, Record<string, string>>>({})

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
  // Clear any error when switching to auto-generate
  clearError(scriptId, paramName)
}

function validate(param: ScriptParameter, value: string): string {
  if (!value) return ''
  const rules = param.validationRules

  if (param.parameterType === 'INTEGER') {
    if (!/^-?\d+$/.test(value.trim())) return 'Must be a whole number'
    const n = parseInt(value, 10)
    if (rules?.min != null && n < rules.min) return `Minimum value is ${rules.min}`
    if (rules?.max != null && n > rules.max) return `Maximum value is ${rules.max}`
  }

  if (param.parameterType === 'PATH') {
    if (!value.startsWith('/')) return 'Must be an absolute path (start with /)'
    if (/\s/.test(value)) return 'Path cannot contain spaces'
  }

  if (param.parameterType === 'STRING' && rules?.pattern) {
    try {
      if (!new RegExp(rules.pattern).test(value)) return `Must match pattern: ${rules.pattern}`
    } catch { /* ignore invalid regex */ }
  }

  if (rules?.minLength != null && value.length < rules.minLength) {
    return `Minimum length is ${rules.minLength}`
  }
  if (rules?.maxLength != null && value.length > rules.maxLength) {
    return `Maximum length is ${rules.maxLength}`
  }

  return ''
}

function setValueAndValidate(scriptId: string, param: ScriptParameter, value: string) {
  setValue(scriptId, param.parameterName, value)
  const err = validate(param, value)
  if (!errors.value[scriptId]) errors.value[scriptId] = {}
  errors.value[scriptId] = { ...errors.value[scriptId], [param.parameterName]: err }
}

function clearError(scriptId: string, paramName: string) {
  if (!errors.value[scriptId]) return
  const { [paramName]: _, ...rest } = errors.value[scriptId]
  errors.value[scriptId] = rest
}

function getError(scriptId: string, paramName: string): string {
  return errors.value[scriptId]?.[paramName] ?? ''
}

const hasErrors = computed(() =>
  Object.values(errors.value).some(scriptErrors =>
    Object.values(scriptErrors).some(e => !!e)
  )
)

watch(hasErrors, (val) => emit('update:hasErrors', val), { immediate: true })

// When scripts (and their pre-filled default values) arrive, run an initial
// validation pass so any default that violates a rule surfaces immediately.
watch(
  () => props.scripts,
  (newScripts) => {
    for (const script of newScripts) {
      for (const param of script.params) {
        const value = getValue(script.id, param.parameterName)
        if (!value) continue
        const scriptErrors = errors.value[script.id] ?? {}
        scriptErrors[param.parameterName] = validate(param, value)
        errors.value[script.id] = scriptErrors
      }
    }
  },
  { deep: true },
)
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
              <span class="ml-1.5 text-[10px] font-mono text-slate-600 uppercase tracking-wider">
                {{ param.parameterType }}
              </span>
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
            @change="setValueAndValidate(script.id, param, ($event.target as HTMLSelectElement).value)"
            class="w-full bg-slate-900 border border-slate-700 rounded-md px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-primary-500"
            :class="{ 'border-red-500': getError(script.id, param.parameterName) }"
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
              @change="setValueAndValidate(script.id, param, String(($event.target as HTMLInputElement).checked))"
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
            step="1"
            :value="getValue(script.id, param.parameterName)"
            :placeholder="param.defaultValue ?? '0'"
            :min="param.validationRules?.min"
            :max="param.validationRules?.max"
            @input="setValueAndValidate(script.id, param, ($event.target as HTMLInputElement).value)"
            class="w-full bg-slate-900 border border-slate-700 rounded-md px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-primary-500 placeholder-slate-600"
            :class="{ 'border-red-500 focus:border-red-500': getError(script.id, param.parameterName) }"
          />

          <!-- PATH input -->
          <input
            v-else-if="param.parameterType === 'PATH'"
            type="text"
            :value="getValue(script.id, param.parameterName)"
            :placeholder="param.defaultValue ?? '/path/to/file'"
            @input="setValueAndValidate(script.id, param, ($event.target as HTMLInputElement).value)"
            class="w-full bg-slate-900 border border-slate-700 rounded-md px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-primary-500 placeholder-slate-600"
            :class="{ 'border-red-500 focus:border-red-500': getError(script.id, param.parameterName) }"
          />

          <!-- STRING text input -->
          <input
            v-else
            type="text"
            :value="getValue(script.id, param.parameterName)"
            :placeholder="param.defaultValue ?? param.displayName"
            @input="setValueAndValidate(script.id, param, ($event.target as HTMLInputElement).value)"
            class="w-full bg-slate-900 border border-slate-700 rounded-md px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-primary-500 placeholder-slate-600"
            :class="{ 'border-red-500 focus:border-red-500': getError(script.id, param.parameterName) }"
          />

          <!-- Inline validation error -->
          <p v-if="getError(script.id, param.parameterName)" class="text-xs text-red-400">
            {{ getError(script.id, param.parameterName) }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
