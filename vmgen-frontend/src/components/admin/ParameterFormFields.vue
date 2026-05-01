<script setup lang="ts">
import { computed } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import type { CreateScriptParameterRequest, ParameterType, ValueSource } from '@/types'

const form = defineModel<CreateScriptParameterRequest>({ required: true })

const props = defineProps<{
  disableParameterName?: boolean
  hasNameConflict?: boolean
  showRandomConfig?: boolean
  showChoices?: boolean
}>()

const emit = defineEmits<{
  'add-choice': []
  'remove-choice': [index: number]
}>()

const typeOptions: { value: ParameterType; label: string; description: string }[] = [
  { value: 'STRING',  label: 'String',  description: 'Free-form text. Supports min/max length validation.' },
  { value: 'INTEGER', label: 'Integer', description: 'Whole number. Supports min/max value validation.' },
  { value: 'CHOICE',  label: 'Choice',  description: 'Value selected from a fixed list. Define the options in the Choices section below.' },
  { value: 'BOOLEAN', label: 'Boolean', description: 'True or false value.' },
  { value: 'PATH',    label: 'Path',    description: 'Filesystem path (e.g. /etc/app/config). Supports length validation.' },
]

const valueSourceOptions: { value: ValueSource; label: string; description: string; warning?: string }[] = [
  {
    value: 'USER_INPUT',
    label: 'User input',
    description: 'The user fills this in when generating the VM. Shown as a form field on the Generate page.',
  },
  {
    value: 'AUTO_RANDOM',
    label: 'Auto random',
    description: 'The system generates a random value at build time. The user never sees or touches it.',
  },
  {
    value: 'AUTO_CHOICE',
    label: 'Auto choice',
    description: 'The system picks one of the choices below at random at build time. The user never sees it.',
    warning: 'You must define at least one choice below, or the build will fail.',
  },
  {
    value: 'USER_INPUT_OR_RANDOM',
    label: 'User input or random',
    description: 'The user can supply a value or let the system randomise it. Shown on the Generate page with an optional override toggle.',
  },
]

const selectedTypeInfo = computed(() =>
  typeOptions.find(o => o.value === form.value.parameterType)
)

const selectedSourceInfo = computed(() =>
  valueSourceOptions.find(o => o.value === form.value.valueSource)
)

const choiceCountWarning = computed(() => {
  if (form.value.valueSource !== 'AUTO_CHOICE' && form.value.parameterType !== 'CHOICE') return false
  return (form.value.choices?.length ?? 0) === 0
})
</script>

<template>
  <div class="space-y-4">
    <!-- Display name + Parameter name -->
    <div class="grid gap-4 md:grid-cols-2">
      <div>
        <label class="mb-1 block text-xs font-medium text-slate-400">Display name</label>
        <input
          v-model="form.displayName"
          type="text"
          autocomplete="off"
          class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          placeholder="Database password"
        />
        <p class="mt-1 text-[11px] text-slate-500">Human-readable label shown on the Generate page.</p>
      </div>
      <div>
        <label class="mb-1 block text-xs font-medium text-slate-400">Parameter name</label>
        <input
          v-model="form.parameterName"
          type="text"
          autocomplete="off"
          class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100 font-mono"
          placeholder="DB_PASSWORD"
          :disabled="props.disableParameterName"
        />
        <p class="mt-1 text-[11px] text-slate-500">
          Uppercase letters, digits and underscores. Referenced in the script as
          <span v-pre class="font-mono text-primary-400">{{PARAMETER_NAME}}</span>.
        </p>
        <p v-if="props.hasNameConflict" class="mt-1 text-[11px] text-red-400">
          Parameter name already used in this script.
        </p>
      </div>
    </div>

    <!-- Description -->
    <div>
      <label class="mb-1 block text-xs font-medium text-slate-400">Description</label>
      <textarea
        v-model="form.description"
        rows="2"
        class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
        placeholder="Explain what this value is used for and any constraints the user should know."
      />
    </div>

    <!-- Type / Value source / Required -->
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
        <p v-if="selectedTypeInfo" class="mt-1 text-[11px] text-slate-500">
          {{ selectedTypeInfo.description }}
        </p>
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
        <p v-if="selectedSourceInfo" class="mt-1 text-[11px] text-slate-500">
          {{ selectedSourceInfo.description }}
        </p>
        <p v-if="selectedSourceInfo?.warning" class="mt-1 text-[11px] text-amber-400">
          ⚠ {{ selectedSourceInfo.warning }}
        </p>
      </div>

      <div class="flex items-start gap-2 pt-6">
        <input v-model="form.isRequired" type="checkbox" class="mt-0.5 rounded border-slate-600 bg-slate-900" />
        <div>
          <span class="text-xs text-slate-300">Required</span>
          <p class="text-[11px] text-slate-500">Fail the build if no value is provided.</p>
        </div>
      </div>
    </div>

    <!-- Default value (user-input sources only) -->
    <div v-if="form.valueSource === 'USER_INPUT' || form.valueSource === 'USER_INPUT_OR_RANDOM'">
      <label class="mb-1 block text-xs font-medium text-slate-400">Default value (optional)</label>
      <input
        v-model="form.defaultValue"
        type="text"
        autocomplete="off"
        class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
        placeholder="Used when the user leaves the field blank"
      />
    </div>

    <!-- Validation rules: STRING / PATH (user-supplied values only) -->
    <div
      v-if="(form.parameterType === 'STRING' || form.parameterType === 'PATH') && (form.valueSource === 'USER_INPUT' || form.valueSource === 'USER_INPUT_OR_RANDOM')"
      class="grid gap-4 md:grid-cols-2"
    >
      <div>
        <label class="mb-1 block text-xs font-medium text-slate-400">Min length (optional)</label>
        <input
          v-model.number="form.validationRules!.minLength"
          type="number"
          min="0"
          placeholder="No minimum"
          class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
        />
      </div>
      <div>
        <label class="mb-1 block text-xs font-medium text-slate-400">Max length (optional)</label>
        <input
          v-model.number="form.validationRules!.maxLength"
          type="number"
          min="0"
          placeholder="No maximum"
          class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
        />
      </div>
    </div>

    <!-- Validation rules: INTEGER (user-supplied values only) -->
    <div v-if="form.parameterType === 'INTEGER' && (form.valueSource === 'USER_INPUT' || form.valueSource === 'USER_INPUT_OR_RANDOM')" class="grid gap-4 md:grid-cols-2">
      <div>
        <label class="mb-1 block text-xs font-medium text-slate-400">Min value (optional)</label>
        <input
          v-model.number="form.validationRules!.min"
          type="number"
          placeholder="No minimum"
          class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
        />
      </div>
      <div>
        <label class="mb-1 block text-xs font-medium text-slate-400">Max value (optional)</label>
        <input
          v-model.number="form.validationRules!.max"
          type="number"
          placeholder="No maximum"
          class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
        />
      </div>
    </div>

    <!-- Random config (optional, shown only in ScriptParameterEditor) -->
    <template v-if="props.showRandomConfig && (form.valueSource === 'AUTO_RANDOM' || form.valueSource === 'USER_INPUT_OR_RANDOM')">
      <!-- Integer: min/max range -->
      <div v-if="form.parameterType === 'INTEGER'" class="grid gap-4 md:grid-cols-2">
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Min value</label>
          <input
            v-model.number="form.randomConfig!.min"
            type="number"
            placeholder="0"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
          <p class="mt-1 text-[11px] text-slate-500">Inclusive lower bound.</p>
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Max value</label>
          <input
            v-model.number="form.randomConfig!.max"
            type="number"
            placeholder="100"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
          <p class="mt-1 text-[11px] text-slate-500">Inclusive upper bound.</p>
        </div>
      </div>
      <!-- String/other: length + charset -->
      <div v-else class="grid gap-4 md:grid-cols-2">
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Random length</label>
          <input
            v-model.number="form.randomConfig!.length"
            type="number"
            min="1"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          />
          <p class="mt-1 text-[11px] text-slate-500">Number of characters to generate.</p>
        </div>
        <div>
          <label class="mb-1 block text-xs font-medium text-slate-400">Charset</label>
          <select
            v-model="form.randomConfig!.charset"
            class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
          >
            <option value="alphanumeric">Alphanumeric (a–z, A–Z, 0–9)</option>
            <option value="alphanumeric_special">Alphanumeric + symbols</option>
            <option value="hex">Hex (0–9, a–f)</option>
            <option value="numeric">Numeric only (0–9)</option>
          </select>
        </div>
      </div>
    </template>

    <!-- Choices editor -->
    <div v-if="props.showChoices && (form.parameterType === 'CHOICE' || form.valueSource === 'AUTO_CHOICE')">
      <div class="flex items-center justify-between mb-1">
        <div>
          <span class="text-xs font-medium text-slate-400">Choices</span>
          <p class="text-[11px] text-slate-500">
            <template v-if="form.valueSource === 'AUTO_CHOICE'">
              The system picks one of these at random at build time.
            </template>
            <template v-else>
              The user picks one of these when generating the VM.
            </template>
          </p>
        </div>
        <BaseButton size="sm" variant="secondary" type="button" @click="emit('add-choice')">
          Add choice
        </BaseButton>
      </div>

      <p v-if="choiceCountWarning" class="mb-2 text-[11px] text-amber-400">
        ⚠ No choices defined yet. Add at least one or the build will fail.
      </p>

      <div v-if="!form.choices?.length" class="text-xs text-slate-500 italic">
        No choices defined yet.
      </div>
      <div v-else class="space-y-2">
        <div
          v-for="(choice, idx) in form.choices"
          :key="idx"
          class="grid gap-2 rounded border border-slate-800 bg-slate-950/80 px-3 py-2 md:grid-cols-[1fr,1fr,auto]"
        >
          <div>
            <label class="mb-0.5 block text-[10px] text-slate-500">Label (shown to user)</label>
            <input
              v-model="choice.label"
              type="text"
              autocomplete="off"
              class="w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-100"
              placeholder="e.g. Easy mode"
            />
          </div>
          <div>
            <label class="mb-0.5 block text-[10px] text-slate-500">Value (used in script)</label>
            <input
              v-model="choice.value"
              type="text"
              autocomplete="off"
              class="w-full rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-100 font-mono"
              placeholder="e.g. easy"
            />
          </div>
          <div class="flex items-end justify-end gap-2 pb-0.5">
            <div>
              <label class="mb-0.5 block text-[10px] text-slate-500">Order</label>
              <input
                v-model.number="choice.displayOrder"
                type="number"
                class="w-14 rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-100"
              />
            </div>
            <button type="button" class="text-xs text-slate-400 hover:text-red-400 pb-1" @click="emit('remove-choice', idx)">
              Remove
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
