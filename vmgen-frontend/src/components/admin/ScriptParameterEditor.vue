<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import type {
  ScriptDetail,
  ScriptParameter,
  ParameterType,
  ValueSource,
  ValidationRules,
  CreateScriptParameterRequest,
  UpdateScriptParameterRequest,
  CreateParameterChoice,
} from '@/types'
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
  displayOrder: (listQuery.data.value?.length || 0),
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

function openCreate() {
  editing.value = null
  form.value = emptyForm()
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
    // `?? {}` guards against null when no rules have been saved yet.
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
  // form uses the superset (create) type, that's fine for editing UI
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

/**
 * Strips fields irrelevant to the current type and removes empty-string
 * artefacts left by v-model.number on untouched number inputs.
 * Returns undefined when no rules are actually set so the backend stores null.
 */
function cleanRules(type: ParameterType, rules: ValidationRules | undefined): ValidationRules | undefined {
  if (!rules) return undefined
  const out: ValidationRules = {}
  if (type === 'INTEGER') {
    if (rules.min != null && rules.min !== ('' as any)) out.min = Number(rules.min)
    if (rules.max != null && rules.max !== ('' as any)) out.max = Number(rules.max)
  } else if (type === 'STRING' || type === 'PATH') {
    if (rules.minLength != null && rules.minLength !== ('' as any)) out.minLength = Number(rules.minLength)
    if (rules.maxLength != null && rules.maxLength !== ('' as any)) out.maxLength = Number(rules.maxLength)
    if (rules.pattern?.trim()) out.pattern = rules.pattern.trim()
  }
  return Object.keys(out).length > 0 ? out : undefined
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
            <p class="text-sm font-medium text-slate-100">
              {{ param.displayName }}
            </p>
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
        <div class="flex gap-2">
          <BaseButton size="sm" variant="secondary" @click="openEdit(param)">
            Edit
          </BaseButton>
          <BaseButton size="sm" variant="ghost" @click="deleteParam(param)">
            Delete
          </BaseButton>
        </div>
      </div>
    </div>

    <!-- Simple inline editor instead of separate modal to keep implementation lean -->
    <div v-if="showForm" class="mt-4 border-t border-slate-800 pt-4 space-y-4">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold text-slate-100">
          {{ editing ? 'Edit parameter' : 'Add parameter' }}
        </h3>
        <button class="text-xs text-slate-400 hover:text-slate-200" @click="closeForm">Close</button>
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
            :disabled="!!editing"
          />
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

      <div
        v-if="form.parameterType === 'STRING' || form.parameterType === 'PATH'"
        class="grid gap-4 md:grid-cols-2"
      >
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Min length</label>
          <input
            v-model.number="form.validationRules!.minLength"
            type="number"
            min="0"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Max length</label>
          <input
            v-model.number="form.validationRules!.maxLength"
            type="number"
            min="0"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
        </div>
      </div>

      <div v-if="form.parameterType === 'INTEGER'" class="grid gap-4 md:grid-cols-2">
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Min value</label>
          <input
            v-model.number="form.validationRules!.min"
            type="number"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Max value</label>
          <input
            v-model.number="form.validationRules!.max"
            type="number"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
        </div>
      </div>

      <div
        v-if="form.valueSource === 'AUTO_RANDOM' || form.valueSource === 'USER_INPUT_OR_RANDOM'"
        class="grid gap-4 md:grid-cols-2"
      >
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Random length</label>
          <input
            v-model.number="form.randomConfig!.length"
            type="number"
            min="1"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Charset</label>
          <select
            v-model="form.randomConfig!.charset"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          >
            <option value="alphanumeric">Alphanumeric</option>
            <option value="alphanumeric_special">Alphanumeric + symbols</option>
            <option value="hex">Hex</option>
            <option value="numeric">Numeric</option>
          </select>
        </div>
      </div>

      <div v-if="form.parameterType === 'CHOICE'">
        <div class="flex items-center justify-between mb-2">
          <span class="text-xs font-medium text-slate-400">Choices</span>
          <BaseButton size="sm" variant="secondary" @click="addChoice">
            Add choice
          </BaseButton>
        </div>
        <div v-if="!form.choices?.length" class="text-xs text-slate-500">
          No choices defined yet.
        </div>
        <div v-else class="space-y-2">
          <div
            v-for="(choice, idx) in form.choices"
            :key="idx"
            class="grid gap-2 rounded border border-slate-800 bg-slate-950/80 px-3 py-2 md:grid-cols-[1fr,1fr,auto]"
          >
            <div>
              <input
                v-model="choice.label"
                type="text"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-100"
                placeholder="Label"
              />
            </div>
            <div>
              <input
                v-model="choice.value"
                type="text"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-100 font-mono"
                placeholder="Value"
              />
            </div>
            <div class="flex items-center justify-end gap-2">
              <input
                v-model.number="choice.displayOrder"
                type="number"
                class="w-14 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-100"
              />
              <button class="text-xs text-slate-400 hover:text-slate-200" @click="removeChoice(idx)">
                Remove
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="flex justify-end gap-3">
        <BaseButton type="button" variant="ghost" @click="closeForm">
          Cancel
        </BaseButton>
        <BaseButton type="button" @click="save">
          Save parameter
        </BaseButton>
      </div>
    </div>
  </BaseCard>
</template>

