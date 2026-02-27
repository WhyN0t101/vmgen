<script setup lang="ts">
import { ref, computed } from 'vue'
import { useTemplates } from '@/composables/useTemplates'
import { useVulnerabilities } from '@/composables/useVulnerabilities'
import { useCreateBuild } from '@/composables/useBuilds'
import TemplateSelector from '@/components/templates/TemplateSelector.vue'
import VulnerabilitySelector from '@/components/vulnerabilities/VulnerabilitySelector.vue'
import ConfigurationPanel from '@/components/generate/ConfigurationPanel.vue'
import type { Template } from '@/types'

const selectedTemplateId = ref<string>()
const selectedVulnIds = ref<string[]>([])
const difficulty = ref<'easy' | 'medium' | 'hard'>('medium')
const telemetryEnabled = ref(false)

const { data: templates, isLoading: templatesLoading } = useTemplates()
const { data: vulnerabilities, isLoading: vulnsLoading } = useVulnerabilities(selectedTemplateId)
const { mutate: createBuild, isPending: isCreating } = useCreateBuild()

const selectedTemplate = computed(() => 
  templates.value?.find(t => t.id === selectedTemplateId.value)
)

const estimatedTime = computed(() => {
  const base = 5
  const perVuln = selectedVulnIds.value.length * 2
  return `${base + perVuln}-${base + perVuln + 4} min`
})

const canSubmit = computed(() => 
  selectedTemplateId.value && selectedVulnIds.value.length > 0
)

function handleSubmit() {
  if (!canSubmit.value || !selectedTemplateId.value) return
  
  createBuild({
    template_id: selectedTemplateId.value,
    vulnerability_ids: selectedVulnIds.value,
    parameters: {
      difficulty: difficulty.value,
      telemetry_enabled: telemetryEnabled.value,
    },
  })
}
</script>

<template>
  <div class="max-w-7xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <div>
        <p class="text-[11px] font-mono tracking-[0.25em] uppercase text-slate-500 mb-1">
          Flow // Generate
        </p>
        <h1 class="text-2xl md:text-3xl font-semibold text-slate-50">
          Generate vulnerable VM
        </h1>
      </div>
      
      <div class="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <!-- Left: Templates -->
        <div class="lg:col-span-1">
          <TemplateSelector
            v-model="selectedTemplateId"
            :templates="templates"
            :loading="templatesLoading"
          />
        </div>
        
        <!-- Center: Vulnerabilities -->
        <div class="lg:col-span-2">
          <VulnerabilitySelector
            v-model="selectedVulnIds"
            :vulnerabilities="vulnerabilities"
            :loading="vulnsLoading"
            :template="selectedTemplate"
          />
        </div>
        
        <!-- Right: Config -->
        <div class="lg:col-span-1">
          <ConfigurationPanel
            v-model:difficulty="difficulty"
            :telemetry-enabled="telemetryEnabled"
            :estimated-time="estimatedTime"
            :can-submit="!!canSubmit"
            :is-submitting="isCreating"
            @update:telemetry="(value: boolean) => telemetryEnabled = value"
            @submit="handleSubmit"
          />
        </div>
      </div>
    </div>
  </div>
</template>

