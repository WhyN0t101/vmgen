<script setup lang="ts">
import { computed } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

interface Props {
  difficulty: 'easy' | 'medium' | 'hard'
  telemetryEnabled: boolean
  estimatedTime?: string
  canSubmit?: boolean
  isSubmitting?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  estimatedTime: '5-10 min',
  canSubmit: false,
  isSubmitting: false,
})

const emit = defineEmits<{
  'update:difficulty': [value: 'easy' | 'medium' | 'hard']
  'update:telemetry': [value: boolean]
  submit: []
}>()

const difficultyOptions: Array<{ value: 'easy' | 'medium' | 'hard'; label: string }> = [
  { value: 'easy', label: 'Easy' },
  { value: 'medium', label: 'Medium' },
  { value: 'hard', label: 'Hard' },
]
</script>

<template>
  <div>
    <h2 class="text-lg font-semibold text-slate-100 mb-4">Configuration</h2>
    
    <BaseCard padding="md" class="space-y-6">
      <!-- Difficulty -->
      <div>
        <label class="block text-sm font-medium text-slate-300 mb-3">
          Difficulty
        </label>
        <div class="flex gap-2">
          <button
            v-for="option in difficultyOptions"
            :key="option.value"
            :class="[
              'flex-1 px-4 py-2 rounded-lg text-sm font-medium transition-colors',
              props.difficulty === option.value
                ? 'bg-primary-500 text-white'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
            ]"
            @click="emit('update:difficulty', option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>
      
      <!-- Options -->
      <div class="space-y-3">
        <label class="flex items-center gap-3 cursor-pointer">
          <input
            type="checkbox"
            :checked="telemetryEnabled"
            @change="emit('update:telemetry', Boolean(($event.target as HTMLInputElement).checked))"
            class="w-4 h-4 rounded border-slate-700 bg-slate-800 text-primary-500 focus:ring-primary-500"
          />
          <span class="text-sm text-slate-300">Enable Telemetry</span>
        </label>
        
      </div>
      
      <div class="border-t border-slate-800 pt-4">
        <div class="text-sm text-slate-400 mb-4">
          Estimated Build Time: <span class="font-semibold text-slate-300">{{ estimatedTime }}</span>
        </div>
        
        <BaseButton
          :disabled="!canSubmit || isSubmitting"
          :loading="isSubmitting"
          class="w-full"
          @click="emit('submit')"
        >
          Generate VM
        </BaseButton>
      </div>
    </BaseCard>
  </div>
</template>

