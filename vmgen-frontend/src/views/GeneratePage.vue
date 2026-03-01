<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useActiveTemplates, usePublicCompatibleScripts } from '@/composables/useTemplates'
import { useCreateBuild } from '@/composables/useBuilds'
import TemplateSelector from '@/components/templates/TemplateSelector.vue'
import VulnerabilitySelector from '@/components/vulnerabilities/VulnerabilitySelector.vue'
import ConfigurationPanel from '@/components/generate/ConfigurationPanel.vue'
import ScriptParameterInputs from '@/components/generate/ScriptParameterInputs.vue'
import { api } from '@/api/endpoints'
import type { Vulnerability, TemplateListResponse, ScriptParameter } from '@/types'

const selectedTemplateId = ref<string | undefined>(undefined)
const selectedVulnIds = ref<string[]>([])
const difficulty = ref<'easy' | 'medium' | 'hard'>('medium')
const telemetryEnabled = ref(false)

const { data: templates, isLoading: templatesLoading } = useActiveTemplates()
const { data: compatibleScripts, isLoading: scriptsLoading } = usePublicCompatibleScripts(selectedTemplateId)
const { mutate: createBuild, isPending: isCreating } = useCreateBuild()

// Parameter state
const scriptParamsCache = ref<Record<string, ScriptParameter[]>>({})
const parameterValues = ref<Record<string, Record<string, string>>>({})
const randomizeFlags = ref<Record<string, Record<string, boolean>>>({})

// Clear all state whenever the template changes.
watch(selectedTemplateId, () => {
  selectedVulnIds.value = []
  scriptParamsCache.value = {}
  parameterValues.value = {}
  randomizeFlags.value = {}
})

// Auto-select scripts matching the chosen difficulty (greedy, respects conflicts + dependencies).
function autoSelectForDifficulty(diff: string, scripts: typeof compatibleScripts.value) {
  if (!scripts?.length) return
  const target = diff.toUpperCase()
  const selected = new Set<string>()

  for (const script of scripts) {
    if (script.difficultyLevel?.toUpperCase() !== target) continue
    // Skip if any already-selected script conflicts with this one
    const blocked = [...selected].some(id =>
      (script.conflictsWithIds ?? []).includes(id) ||
      (scripts.find(s => s.id === id)?.conflictsWithIds ?? []).includes(script.id)
    )
    if (blocked) continue
    selected.add(script.id)
    // Pull in required dependencies
    for (const reqId of (script.requiresIds ?? [])) selected.add(reqId)
  }
  selectedVulnIds.value = [...selected]
}

// Re-run auto-select whenever difficulty button changes or scripts load for a new template.
watch(difficulty, (diff) => autoSelectForDifficulty(diff, compatibleScripts.value))
watch(compatibleScripts, (scripts) => autoSelectForDifficulty(difficulty.value, scripts))

// Fetch USER_INPUT parameters for each newly selected script.
watch(selectedVulnIds, async (newIds, oldIds) => {
  const oldSet = new Set(oldIds ?? [])
  const newSet = new Set(newIds)

  // Drop cache for scripts that were deselected.
  for (const id of oldSet) {
    if (!newSet.has(id)) {
      const { [id]: _p, ...restP } = scriptParamsCache.value
      scriptParamsCache.value = restP
      const { [id]: _v, ...restV } = parameterValues.value
      parameterValues.value = restV
      const { [id]: _r, ...restR } = randomizeFlags.value
      randomizeFlags.value = restR
    }
  }

  // Fetch params for scripts not yet in cache.
  const toFetch = newIds.filter(id => !(id in scriptParamsCache.value))
  if (!toFetch.length) return

  await Promise.all(
    toFetch.map(async (id) => {
      try {
        const params = await api.scripts.getParameters(id)
        scriptParamsCache.value = { ...scriptParamsCache.value, [id]: params }
        // Pre-fill default values. Use != null so "0" and other falsy-but-valid
        // defaults (e.g. integer zero) are still applied.
        const defaults: Record<string, string> = {}
        for (const p of params) {
          if (p.defaultValue != null && p.defaultValue !== '') defaults[p.parameterName] = p.defaultValue
        }
        if (Object.keys(defaults).length) {
          parameterValues.value = {
            ...parameterValues.value,
            [id]: { ...defaults, ...(parameterValues.value[id] ?? {}) },
          }
        }
      } catch {
        scriptParamsCache.value = { ...scriptParamsCache.value, [id]: [] }
      }
    })
  )
})

const selectedTemplate = computed<TemplateListResponse | undefined>(() =>
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
    description: s.description,
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

// Scripts that have user-facing parameters, grouped for the parameter input component.
const scriptsWithUserInputParams = computed(() =>
  selectedVulnIds.value
    .map(id => ({
      id,
      name: compatibleScripts.value?.find(s => s.id === id)?.name ?? id,
      params: (scriptParamsCache.value[id] ?? []).filter(
        p => p.valueSource === 'USER_INPUT' || p.valueSource === 'USER_INPUT_OR_RANDOM'
      ),
    }))
    .filter(s => s.params.length > 0)
)

const estimatedTime = computed(() => {
  const base = 5
  const perVuln = selectedVulnIds.value.length * 2
  return `${base + perVuln}-${base + perVuln + 4} min`
})

// All required USER_INPUT params must be filled before submitting.
const requiredParamsFilled = computed(() => {
  for (const { id, params } of scriptsWithUserInputParams.value) {
    for (const param of params) {
      const isRand = randomizeFlags.value[id]?.[param.parameterName] ?? false
      if (isRand) continue
      if (param.isRequired) {
        if (!parameterValues.value[id]?.[param.parameterName]?.trim()) return false
      }
    }
  }
  return true
})

const parameterErrors = ref(false)

const canSubmit = computed(() =>
  !!selectedTemplateId.value && selectedVulnIds.value.length > 0 && requiredParamsFilled.value && !parameterErrors.value
)

function handleSubmit() {
  if (!canSubmit.value || !selectedTemplateId.value) return

  // Collect non-empty parameter values for selected scripts.
  const scriptParams: Record<string, Record<string, string>> = {}
  for (const [scriptId, values] of Object.entries(parameterValues.value)) {
    if (!selectedVulnIds.value.includes(scriptId)) continue
    const filtered: Record<string, string> = {}
    for (const [paramName, value] of Object.entries(values)) {
      if (randomizeFlags.value[scriptId]?.[paramName]) continue
      if (value?.trim()) filtered[paramName] = value.trim()
    }
    if (Object.keys(filtered).length) scriptParams[scriptId] = filtered
  }

  createBuild({
    templateId: selectedTemplateId.value,
    vulnerabilityIds: selectedVulnIds.value,
    difficulty: difficulty.value,
    telemetryEnabled: telemetryEnabled.value,
    ...(Object.keys(scriptParams).length ? { scriptParameters: scriptParams } : {}),
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

        <!-- Center: Vulnerabilities + Parameter Inputs -->
        <div class="lg:col-span-2">
          <VulnerabilitySelector
            v-model="selectedVulnIds"
            :vulnerabilities="vulnerabilities"
            :loading="scriptsLoading"
            :template="selectedTemplate"
            :conflict-map="conflictMap"
            :requires-map="requiresMap"
          />

          <ScriptParameterInputs
            v-model="parameterValues"
            v-model:randomize-flags="randomizeFlags"
            v-model:has-errors="parameterErrors"
            :scripts="scriptsWithUserInputParams"
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
