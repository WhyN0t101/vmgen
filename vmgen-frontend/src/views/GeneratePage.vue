<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useTemplates, usePublicCompatibleScripts } from '@/composables/useTemplates'
import { useCreateBuild } from '@/composables/useBuilds'
import TemplateSelector from '@/components/templates/TemplateSelector.vue'
import VulnerabilitySelector from '@/components/vulnerabilities/VulnerabilitySelector.vue'
import ConfigurationPanel from '@/components/generate/ConfigurationPanel.vue'
import type { Vulnerability, Template } from '@/types'

const selectedTemplateId = ref<string | null>(null)
const selectedVulnIds = ref<string[]>([])
const difficulty = ref<'easy' | 'medium' | 'hard'>('medium')
const telemetryEnabled = ref(false)

const { data: templates, isLoading: templatesLoading } = useTemplates()
const { data: compatibleScripts, isLoading: scriptsLoading } = usePublicCompatibleScripts(selectedTemplateId)
const { mutate: createBuild, isPending: isCreating } = useCreateBuild()

// Clear selections whenever the template changes so stale IDs are not submitted.
watch(selectedTemplateId, () => { selectedVulnIds.value = [] })

const selectedTemplate = computed<Template | undefined>(() =>
  templates.value?.find(t => t.id === selectedTemplateId.value)
)

// Derive vulnerability list from the real compatible-scripts response so IDs
// always match the conflict / dependency maps below.
const vulnerabilities = computed<Vulnerability[]>(() =>
  (compatibleScripts.value ?? []).map(s => ({
    id: s.id,
    name: s.name,
    category: s.categoryName ?? 'Other',
    difficulty: s.difficultyLevel,
    requires_capabilities: [],
    description: undefined,
  }))
)

// Conflict map: scriptId -> list of conflicting scriptIds (within the compatible set).
const conflictMap = computed<Record<string, string[]>>(() => {
  const map: Record<string, string[]> = {}
  for (const s of (compatibleScripts.value ?? [])) {
    if (s.conflictsWithIds && s.conflictsWithIds.length > 0) {
      map[s.id] = s.conflictsWithIds
    }
  }
  return map
})

// Dependency map: scriptId -> list of required scriptIds.
const requiresMap = computed<Record<string, string[]>>(() => {
  const map: Record<string, string[]> = {}
  for (const s of (compatibleScripts.value ?? [])) {
    if (s.requiresIds && s.requiresIds.length > 0) {
      map[s.id] = s.requiresIds
    }
  }
  return map
})

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
            :loading="scriptsLoading"
            :template="selectedTemplate"
            :conflict-map="conflictMap"
            :requires-map="requiresMap"
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

