<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import ParameterFormFields from '@/components/admin/ParameterFormFields.vue'
import type { CreateScriptParameterRequest, CreateParameterChoice } from '@/types'
import { useToast } from '@/composables/useToast'
import { cleanRules, defaultParameterForm } from '@/utils/parameterRules'

const props = defineProps<{
  parameters: CreateScriptParameterRequest[]
}>()

const emit = defineEmits<{
  'update:parameters': [value: CreateScriptParameterRequest[]]
}>()

const { show } = useToast()

const editingIndex = ref<number | null>(null)
const showForm = ref(false)

const form = ref<CreateScriptParameterRequest>(defaultParameterForm())

const hasNameConflict = computed(() => {
  const name = form.value.parameterName?.trim()
  if (!name) return false
  return (props.parameters ?? []).some((p, idx) => p.parameterName === name && idx !== editingIndex.value)
})

function openCreate() {
  editingIndex.value = null
  form.value = defaultParameterForm(props.parameters?.length ?? 0)
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

function deleteParam(index: number) {
  const next = [...(props.parameters ?? [])]
  next.splice(index, 1)
  emit('update:parameters', next)
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

function save() {
  if (!form.value.displayName?.trim() || !form.value.parameterName?.trim()) {
    show('Parameter name and display name are required.', 'error')
    return
  }
  if (!/^[A-Z_][A-Z0-9_]*$/.test(form.value.parameterName.trim())) {
    show('Parameter name must be uppercase letters, digits, and underscores (e.g., DATABASE_PASSWORD, VAR1).', 'error')
    return
  }
  if (hasNameConflict.value) {
    show('A parameter with this name already exists in this script.', 'error')
    return
  }
  if (
    (form.value.parameterType === 'CHOICE' || form.value.valueSource === 'AUTO_CHOICE')
    && !(form.value.choices?.length)
  ) {
    show('Add at least one choice for this parameter.', 'error')
    return
  }
  if (form.value.choices?.some(c => !c.label?.trim() || !c.value?.trim())) {
    show('Each choice needs both a label and a value.', 'error')
    return
  }

  const next = [...(props.parameters ?? [])]
  const payload: CreateScriptParameterRequest = {
    ...form.value,
    parameterName: form.value.parameterName.trim(),
    displayName: form.value.displayName.trim(),
    displayOrder: form.value.displayOrder ?? next.length,
    validationRules: cleanRules(form.value.parameterType, form.value.validationRules),
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
          <p v-if="param.description" class="text-xs text-slate-400">{{ param.description }}</p>
          <p class="text-[11px] text-slate-500">
            {{ param.parameterType }} · {{ param.valueSource }}
            <span v-if="param.isRequired" class="ml-1 text-red-400">· required</span>
          </p>
        </div>
        <div class="flex gap-2 shrink-0">
          <BaseButton type="button" size="sm" variant="secondary" @click="openEdit(idx)">Edit</BaseButton>
          <BaseButton type="button" size="sm" variant="ghost" @click="deleteParam(idx)">Delete</BaseButton>
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

      <ParameterFormFields
        v-model="form"
        :has-name-conflict="hasNameConflict"
        show-random-config
        show-choices
        @add-choice="addChoice"
        @remove-choice="removeChoice"
      />

      <div class="flex justify-end gap-2">
        <BaseButton type="button" variant="ghost" size="sm" @click="closeForm">Cancel</BaseButton>
        <BaseButton type="button" size="sm" :disabled="hasNameConflict" @click="save">Save</BaseButton>
      </div>
    </div>
  </BaseCard>
</template>
