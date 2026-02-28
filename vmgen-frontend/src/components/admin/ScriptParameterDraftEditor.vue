<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import type {
  CreateScriptParameterRequest,
  ParameterType,
  ValueSource,
  CreateParameterChoice,
} from '@/types'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  parameters: CreateScriptParameterRequest[]
}>()

const emit = defineEmits<{
  'update:parameters': [value: CreateScriptParameterRequest[]]
}>()

const { show } = useToast()

const editingIndex = ref<number | null>(null)
const showForm = ref(false)

const emptyForm = (): CreateScriptParameterRequest => ({
  parameterName: '',
  displayName: '',
  description: '',
  parameterType: 'STRING',
  valueSource: 'USER_INPUT',
  defaultValue: '',
  isRequired: true,
  validationRules: {},
  randomConfig: {},
  userCanOverride: false,
  displayOrder: props.parameters?.length ?? 0,
  choices: [],
})

const form = ref<CreateScriptParameterRequest>(emptyForm())

const valueSourceOptions: { value: ValueSource; label: string }[] = [
  { value: 'USER_INPUT', label: 'User input' },
  { value: 'AUTO_RANDOM', label: 'Auto random' },
  { value: 'AUTO_CHOICE', label: 'Auto choice' },
  { value: 'USER_INPUT_OR_RANDOM', label: 'User input or random' },
]

const typeOptions: { value: ParameterType; label: string }[] = [
  { value: 'STRING', label: 'String' },
  { value: 'INTEGER', label: 'Integer' },
  { value: 'CHOICE', label: 'Choice' },
  { value: 'BOOLEAN', label: 'Boolean' },
  { value: 'PATH', label: 'Path' },
]

const hasNameConflict = computed(() => {
  const name = form.value.parameterName?.trim()
  if (!name) return false
  return (props.parameters ?? []).some((p, idx) => p.parameterName === name && idx !== editingIndex.value)
})

function openCreate() {
  editingIndex.value = null
  form.value = emptyForm()
  showForm.value = true
}

function openEdit(index: number) {
  editingIndex.value = index
  form.value = JSON.parse(JSON.stringify(props.parameters[index])) as CreateScriptParameterRequest
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editingIndex.value = null
}

function addChoice() {
  if (!form.value.choices) form.value.choices = []
  const choice: CreateParameterChoice = {
    value: '',
    label: '',
    description: '',
    displayOrder: form.value.choices.length,
  }
  form.value.choices.push(choice)
}

function removeChoice(index: number) {
  form.value.choices?.splice(index, 1)
}

function deleteParam(index: number) {
  const next = [...(props.parameters ?? [])]
  next.splice(index, 1)
  emit('update:parameters', next)
}

function save() {
  if (!form.value.displayName?.trim() || !form.value.parameterName?.trim()) {
    show('Parameter name and display name are required.', 'error')
    return
  }
  if (!/^[A-Z_]+$/.test(form.value.parameterName.trim())) {
    show('Parameter name must be uppercase with underscores (e.g., DATABASE_PASSWORD).', 'error')
    return
  }
  if (hasNameConflict.value) {
    show('A parameter with this name already exists in this script.', 'error')
    return
  }

  const next = [...(props.parameters ?? [])]
  const payload: CreateScriptParameterRequest = {
    ...form.value,
    parameterName: form.value.parameterName.trim(),
    displayName: form.value.displayName.trim(),
    displayOrder: form.value.displayOrder ?? next.length,
  }

  if (editingIndex.value != null) {
    next.splice(editingIndex.value, 1, payload)
  } else {
    next.push(payload)
  }

  emit('update:parameters', next)
  closeForm()
}
</script>

<template>
  <BaseCard class="space-y-4">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold text-slate-100">Parameters</h2>
        <p class="text-xs text-slate-400">
          Define variables referenced in the script as <span v-pre class="font-mono text-primary-400">{{PARAMETER_NAME}}</span>.
          The worker resolves each one before executing the script.
        </p>
      </div>
      <BaseButton type="button" size="sm" variant="secondary" @click="openCreate">
        Add parameter
      </BaseButton>
    </div>

    <div v-if="(parameters?.length ?? 0) === 0" class="text-sm text-slate-400">
      No parameters defined. This script will run with its built-in configuration.
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="(param, idx) in parameters"
        :key="`${param.parameterName}-${idx}`"
        class="flex items-start justify-between rounded border border-slate-800 bg-slate-950/70 px-4 py-3"
      >
        <div class="space-y-1 min-w-0">
          <div class="flex items-center gap-2">
            <p class="text-sm font-medium text-slate-100 truncate">{{ param.displayName }}</p>
            <span class="rounded bg-slate-800 px-2 py-0.5 text-[11px] font-mono text-slate-400">
              {{ param.parameterName }}
            </span>
          </div>
          <p v-if="param.description" class="text-xs text-slate-400">
            {{ param.description }}
          </p>
          <p class="text-[11px] text-slate-500">
            {{ param.parameterType }} · {{ param.valueSource }}
            <span v-if="param.isRequired" class="ml-1 text-red-400">· required</span>
          </p>
        </div>
        <div class="flex gap-2 shrink-0">
          <BaseButton type="button" size="sm" variant="secondary" @click="openEdit(idx)">
            Edit
          </BaseButton>
          <BaseButton type="button" size="sm" variant="ghost" @click="deleteParam(idx)">
            Delete
          </BaseButton>
        </div>
      </div>
    </div>

    <div v-if="showForm" class="mt-4 border-t border-slate-800 pt-4 space-y-4">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-100">
          {{ editingIndex != null ? 'Edit parameter' : 'Add parameter' }}
        </h3>
        <button type="button" class="text-xs text-slate-400 hover:text-slate-200" @click="closeForm">
          Close
        </button>
      </div>

      <div class="grid gap-4 md:grid-cols-2">
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Display name</label>
          <input
            v-model="form.displayName"
            type="text"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Parameter name</label>
          <input
            v-model="form.parameterName"
            type="text"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 font-mono"
            placeholder="DATABASE_PASSWORD"
          />
          <p v-if="hasNameConflict" class="mt-1 text-[11px] text-red-400">
            Parameter name already used.
          </p>
        </div>
      </div>

      <div>
        <label class="mb-1 block text-xs font-medium text-slate-400">Description</label>
        <textarea
          v-model="form.description"
          rows="2"
          class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
        />
      </div>

      <div class="grid gap-4 md:grid-cols-3">
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Type</label>
          <select
            v-model="form.parameterType"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          >
            <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Value source</label>
          <select
            v-model="form.valueSource"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          >
            <option v-for="opt in valueSourceOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>
        <div class="flex items-center gap-2 pt-5">
          <input v-model="form.isRequired" type="checkbox" class="rounded border-slate-600 bg-slate-900" />
          <span class="text-xs text-slate-300">Required</span>
        </div>
      </div>

      <div v-if="form.valueSource === 'USER_INPUT' || form.valueSource === 'USER_INPUT_OR_RANDOM'">
        <label class="mb-1 block text-xs font-medium text-slate-400">Default value (optional)</label>
        <input
          v-model="form.defaultValue"
          type="text"
          class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
        />
      </div>

      <div class="flex justify-end gap-2">
        <BaseButton type="button" variant="ghost" size="sm" @click="closeForm">Cancel</BaseButton>
        <BaseButton type="button" size="sm" :disabled="hasNameConflict" @click="save">
          Save
        </BaseButton>
      </div>
    </div>
  </BaseCard>
</template>

