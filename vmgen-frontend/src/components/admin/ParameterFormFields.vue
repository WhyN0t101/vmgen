<script setup lang="ts">
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

const typeOptions: { value: ParameterType; label: string }[] = [
  { value: 'STRING', label: 'String' },
  { value: 'INTEGER', label: 'Integer' },
  { value: 'CHOICE', label: 'Choice' },
  { value: 'BOOLEAN', label: 'Boolean' },
  { value: 'PATH', label: 'Path' },
]

const valueSourceOptions: { value: ValueSource; label: string }[] = [
  { value: 'USER_INPUT', label: 'User input' },
  { value: 'AUTO_RANDOM', label: 'Auto random' },
  { value: 'AUTO_CHOICE', label: 'Auto choice' },
  { value: 'USER_INPUT_OR_RANDOM', label: 'User input or random' },
]
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
          :disabled="props.disableParameterName"
        />
        <p v-if="props.hasNameConflict" class="mt-1 text-[11px] text-red-400">
          Parameter name already used.
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

    <!-- Default value (user-input sources only) -->
    <div v-if="form.valueSource === 'USER_INPUT' || form.valueSource === 'USER_INPUT_OR_RANDOM'">
      <label class="mb-1 block text-xs font-medium text-slate-400">Default value (optional)</label>
      <input
        v-model="form.defaultValue"
        type="text"
        class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
      />
    </div>

    <!-- Validation rules: STRING / PATH -->
    <div
      v-if="form.parameterType === 'STRING' || form.parameterType === 'PATH'"
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

    <!-- Validation rules: INTEGER -->
    <div v-if="form.parameterType === 'INTEGER'" class="grid gap-4 md:grid-cols-2">
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
    <div
      v-if="props.showRandomConfig && (form.valueSource === 'AUTO_RANDOM' || form.valueSource === 'USER_INPUT_OR_RANDOM')"
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

    <!-- Choices editor (optional, shown only in ScriptParameterEditor) -->
    <div v-if="props.showChoices && form.parameterType === 'CHOICE'">
      <div class="flex items-center justify-between mb-2">
        <span class="text-xs font-medium text-slate-400">Choices</span>
        <BaseButton size="sm" variant="secondary" type="button" @click="emit('add-choice')">
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
            <button type="button" class="text-xs text-slate-400 hover:text-slate-200" @click="emit('remove-choice', idx)">
              Remove
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
