<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import ParameterFormFields from '@/components/admin/ParameterFormFields.vue'
import type {
  ScriptDetail,
  ScriptParameter,
  CreateScriptParameterRequest,
  UpdateScriptParameterRequest,
  CreateParameterChoice,
} from '@/types'
import { cleanRules, defaultParameterForm } from '@/utils/parameterRules'
import { useScriptParameters } from '@/composables/useScripts'
import { useRoute } from 'vue-router'
import { useConfirm } from '@/composables/useConfirm'

const props = defineProps<{
  script: ScriptDetail
}>()

const route = useRoute()
const scriptId = computed(() => (route.params.id as string) || '')

const { listQuery, createMutation, updateMutation, deleteMutation } = useScriptParameters(scriptId)
const { confirm } = useConfirm()

const editing = ref<ScriptParameter | null>(null)
const showForm = ref(false)

const form = ref<CreateScriptParameterRequest>(defaultParameterForm())

function openCreate() {
  editing.value = null
  form.value = defaultParameterForm(listQuery.data.value?.length || 0)
  showForm.value = true
}

function openEdit(param: ScriptParameter) {
  editing.value = param
  const base: UpdateScriptParameterRequest = {
    displayName: param.displayName,
    description: param.description,
    parameterType: param.parameterType,
    valueSource: param.valueSource,
    defaultValue: param.defaultValue,
    isRequired: param.isRequired,
    // Deep-clone so v-model mutations never touch TanStack Query's cache object.
    validationRules: { ...(param.validationRules ?? {}) },
    randomConfig: { ...(param.randomConfig ?? {}) },
    userCanOverride: param.userCanOverride,
    displayOrder: param.displayOrder,
    choices: param.choices?.map(c => ({
      value: c.value,
      label: c.label,
      description: c.description,
      displayOrder: c.displayOrder,
    })),
  }
  form.value = { parameterName: param.parameterName, ...base }
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editing.value = null
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


async function save() {
  if (editing.value) {
    const payload: UpdateScriptParameterRequest = {
      displayName: form.value.displayName,
      description: form.value.description,
      parameterType: form.value.parameterType,
      valueSource: form.value.valueSource,
      defaultValue: form.value.defaultValue,
      isRequired: form.value.isRequired,
      validationRules: cleanRules(form.value.parameterType, form.value.validationRules),
      randomConfig: form.value.randomConfig,
      userCanOverride: form.value.userCanOverride,
      displayOrder: form.value.displayOrder,
      choices: form.value.choices,
    }
    await updateMutation.mutateAsync({
      scriptId: props.script.id,
      parameterId: editing.value.id,
      data: payload,
    })
  } else {
    await createMutation.mutateAsync({
      scriptId: props.script.id,
      data: {
        ...form.value,
        validationRules: cleanRules(form.value.parameterType, form.value.validationRules),
      },
    })
  }
  closeForm()
}

async function deleteParam(param: ScriptParameter) {
  const ok = await confirm({
    title: 'Delete parameter',
    message: `Delete parameter ${param.displayName}?`,
    confirmLabel: 'Delete',
  })
  if (!ok) return

  await deleteMutation.mutateAsync({ scriptId: props.script.id, parameterId: param.id })
}
</script>

<template>
  <BaseCard class="space-y-4">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold text-slate-100">Parameters</h2>
        <p class="text-xs text-slate-400">
          Optional inputs or auto-generated values this script can use during execution.
        </p>
      </div>
      <BaseButton size="sm" variant="secondary" @click="openCreate">
        Add parameter
      </BaseButton>
    </div>

    <div v-if="listQuery.isLoading.value" class="text-sm text-slate-400">
      Loading parameters…
    </div>

    <div v-else-if="(listQuery.data.value?.length ?? 0) === 0" class="text-sm text-slate-400">
      No parameters defined. This script will run with its built-in configuration.
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="param in listQuery.data.value"
        :key="param.id"
        class="flex items-start justify-between rounded border border-slate-800 bg-slate-950/70 px-4 py-3"
      >
        <div class="space-y-1">
          <div class="flex items-center gap-2">
            <p class="text-sm font-medium text-slate-100">{{ param.displayName }}</p>
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
        <div class="flex gap-2">
          <BaseButton size="sm" variant="secondary" @click="openEdit(param)">Edit</BaseButton>
          <BaseButton size="sm" variant="ghost" @click="deleteParam(param)">Delete</BaseButton>
        </div>
      </div>
    </div>

    <div v-if="showForm" class="mt-4 border-t border-slate-800 pt-4 space-y-4">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-100">
          {{ editing ? 'Edit parameter' : 'Add parameter' }}
        </h3>
        <button class="text-xs text-slate-400 hover:text-slate-200" @click="closeForm">Close</button>
      </div>

      <ParameterFormFields
        v-model="form"
        :disable-parameter-name="!!editing"
        :show-random-config="true"
        :show-choices="true"
        @add-choice="addChoice"
        @remove-choice="removeChoice"
      />

      <div class="flex justify-end gap-3">
        <BaseButton type="button" variant="ghost" @click="closeForm">Cancel</BaseButton>
        <BaseButton type="button" @click="save">Save parameter</BaseButton>
      </div>
    </div>
  </BaseCard>
</template>
