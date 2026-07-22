<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBuildStatus } from '@/composables/useBuildStatus'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import BaseProgress from '@/components/ui/BaseProgress.vue'
import { formatTimeAgoVerbose as formatTimeAgo, formatTimestamp } from '@/utils/dateFormat'
import { api } from '@/api/endpoints'
import type { BuildLogEntry } from '@/types'

// Pipeline stage definitions — order matters for the step list
const PIPELINE_STAGES: { key: string; label: string; percent: number }[] = [
  { key: 'VERIFY_TEMPLATE',     label: 'Verify template',      percent: 5  },
  { key: 'CLONE_TEMPLATE',      label: 'Clone template',       percent: 10 },
  { key: 'CONFIGURE_NETWORK',   label: 'Configure network',    percent: 18 },
  { key: 'START_VM',            label: 'Start VM',             percent: 22 },
  { key: 'WAIT_FOR_SSH',        label: 'Wait for SSH',         percent: 27 },
  { key: 'EXECUTE_SCRIPTS',     label: 'Execute scripts',      percent: 45 },
  { key: 'GENERATE_FLAGS',      label: 'Generate flags',       percent: 60 },
  { key: 'PLACE_FLAGS',         label: 'Place flags',          percent: 65 },
  { key: 'SANITIZE_VM',         label: 'Sanitize VM',          percent: 70 },
  { key: 'SHUTDOWN_VM',         label: 'Shut down VM',         percent: 75 },
  { key: 'REMOVE_NETWORK',      label: 'Remove network',       percent: 80 },
  { key: 'EXPORTING',            label: 'Export OVA',           percent: 87 },
  { key: 'COMPUTE_HASHES',      label: 'Compute hashes',       percent: 91 },
  { key: 'ASSEMBLE_PROVENANCE', label: 'Assemble provenance',  percent: 94 },
  { key: 'REGISTER_COMPLETION', label: 'Register completion',  percent: 97 },
  { key: 'CLEANUP',             label: 'Clean up',             percent: 99 },
]

const STAGE_INDEX = Object.fromEntries(PIPELINE_STAGES.map((s, i) => [s.key, i]))

const route = useRoute()
const requestId = computed(() => route.params.requestId as string)
const { data: build, isLoading } = useBuildStatus(requestId)

const downloading = ref(false)
const downloadProgress = ref(0)
const downloadError = ref<string | null>(null)
const downloadingProvenance = ref(false)

// Flag submission — per-script state, shared attempt counter
interface ScriptFlagState {
  input: string
  submitting: boolean
  correct: boolean
  error: string | null
}

const flagStates = ref<Record<string, ScriptFlagState>>({})
const attemptsRemaining = ref<number | null>(null)

watch(
  () => build.value?.scripts,
  (scripts) => {
    if (!scripts) return
    for (const s of scripts) {
      const state = flagStates.value[s.scriptId]
      if (!state) {
        flagStates.value[s.scriptId] = { input: '', submitting: false, correct: !!s.flagSolvedAt, error: null }
      } else if (s.flagSolvedAt && !state.correct) {
        state.correct = true
      }
    }
  },
  { immediate: true },
)

const allFlagsCorrect = computed(() => {
  const scripts = build.value?.scripts
  return !!scripts?.length && scripts.every(s => flagStates.value[s.scriptId]?.correct)
})

const flagExhausted = computed(() => attemptsRemaining.value === 0)

async function submitFlag(scriptId: string) {
  const state = flagStates.value[scriptId]
  if (!build.value || !state?.input.trim()) return
  state.submitting = true
  state.error = null
  try {
    const result = await api.submitFlag(build.value.id, state.input.trim())
    attemptsRemaining.value = result.attemptsRemaining
    if (result.correct) {
      state.correct = true
    } else {
      state.error = `Incorrect. ${result.attemptsRemaining} attempt${result.attemptsRemaining === 1 ? '' : 's'} remaining.`
    }
  } catch (e: any) {
    state.error = e?.response?.data?.message ?? e?.message ?? 'Submission failed'
  } finally {
    state.submitting = false
  }
}

// Telemetry upload
const telemetryFile = ref<File | null>(null)
const telemetryUploading = ref(false)
const telemetrySubmitted = ref(false)
const telemetryError = ref<string | null>(null)

// Telemetry deletion
const telemetryDeleting = ref(false)
const telemetryDeleted = ref(false)
const telemetryDeleteError = ref<string | null>(null)

async function deleteTelemetry() {
  if (!build.value) return
  telemetryDeleting.value = true
  telemetryDeleteError.value = null
  try {
    await api.deleteTelemetry(build.value.id)
    telemetryDeleted.value = true
    telemetrySubmitted.value = false
  } catch (e: any) {
    telemetryDeleteError.value = e?.response?.data?.message ?? e?.message ?? 'Deletion failed'
  } finally {
    telemetryDeleting.value = false
  }
}

// Build logs
const logsVisible = ref(false)
const buildLogs = ref<BuildLogEntry[]>([])
const logsLoading = ref(false)
const logsError = ref<string | null>(null)

async function toggleLogs() {
  logsVisible.value = !logsVisible.value
  if (logsVisible.value && buildLogs.value.length === 0 && !logsLoading.value) {
    await loadLogs()
  }
}

async function loadLogs() {
  if (!build.value) return
  logsLoading.value = true
  logsError.value = null
  try {
    buildLogs.value = await api.getBuildLogs(build.value.id)
  } catch (e: any) {
    logsError.value = e?.response?.data?.message ?? e?.message ?? 'Failed to load logs'
  } finally {
    logsLoading.value = false
  }
}

const logLevelClass: Record<string, string> = {
  INFO:    'text-slate-300',
  WARN:    'text-amber-400',
  WARNING: 'text-amber-400',
  ERROR:   'text-red-400',
  DEBUG:   'text-slate-500',
}

function onTelemetryFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  telemetryFile.value = input.files?.[0] ?? null
  telemetryError.value = null
}

async function submitTelemetry() {
  if (!build.value || !telemetryFile.value) return
  telemetryUploading.value = true
  telemetryError.value = null
  try {
    const text = await telemetryFile.value.text()
    const json = JSON.parse(text)
    await api.uploadTelemetry(build.value.id, json)
    telemetrySubmitted.value = true
  } catch (e: any) {
    telemetryError.value = e?.response?.data?.message ?? e?.message ?? 'Upload failed'
  } finally {
    telemetryUploading.value = false
  }
}

async function downloadOva() {
  if (!build.value) return
  downloading.value = true
  downloadProgress.value = 0
  downloadError.value = null
  try {
    const filename = build.value.ovaFilename ?? `vm-${build.value.id}.ova`
    await api.downloadBuild(build.value.id, filename, build.value.ovaSha256, (received, total) => {
      downloadProgress.value = total > 0 ? Math.round((received / total) * 100) : 0
    })
  } catch (e: any) {
    downloadError.value = e?.response?.data?.message ?? e?.message ?? 'Download failed'
  } finally {
    downloading.value = false
  }
}

async function downloadManifest() {
  if (!build.value) return
  downloadingProvenance.value = true
  try {
    await api.downloadProvenance(build.value.id)
  } finally {
    downloadingProvenance.value = false
  }
}

// Clone (retry) failed build
const cloning = ref(false)
const cloneError = ref<string | null>(null)
const router = useRouter()

async function retryBuild() {
  if (!build.value) return
  cloning.value = true
  cloneError.value = null
  try {
    const newBuild = await api.cloneBuild(build.value.id)
    router.push({ name: 'build-status', params: { requestId: newBuild.id } })
  } catch (e: any) {
    cloneError.value = e?.response?.data?.message ?? e?.message ?? 'Failed to retry build'
  } finally {
    cloning.value = false
  }
}

const stateConfig = {
  queued:    { label: 'Queued',    color: 'info'    },
  building:  { label: 'Building',  color: 'info'    },
  exporting: { label: 'Exporting', color: 'info'    },
  ready:     { label: 'Ready',     color: 'success' },
  failed:    { label: 'Failed',    color: 'danger'  },
  archived:  { label: 'Archived',  color: 'default' },
} as const

const stateInfo = computed(() => {
  if (!build.value) return null
  return stateConfig[build.value.state as keyof typeof stateConfig] ?? null
})

const isActive = computed(() =>
  build.value?.state === 'queued' ||
  build.value?.state === 'building' ||
  build.value?.state === 'exporting'
)

const progressPercent = computed(() => {
  if (!build.value) return 0
  const { state, currentWorkerStage } = build.value
  if (state === 'ready' || state === 'archived') return 100
  if (state === 'failed') return 0
  if (state === 'queued') return 2
  const stage = PIPELINE_STAGES.find(s => s.key === currentWorkerStage?.toUpperCase())
  return stage?.percent ?? (state === 'exporting' ? 85 : 15)
})

const currentStageIndex = computed(() => {
  const key = build.value?.currentWorkerStage?.toUpperCase()
  return key !== undefined ? (STAGE_INDEX[key] ?? -1) : -1
})

// Elapsed time ticker
const elapsedSeconds = ref(0)
let elapsedTimer: ReturnType<typeof setInterval> | null = null

function startElapsed() {
  if (elapsedTimer) return
  elapsedTimer = setInterval(() => {
    if (!build.value?.submittedAt) return
    elapsedSeconds.value = Math.floor((Date.now() - new Date(build.value.submittedAt).getTime()) / 1000)
  }, 1000)
}

function stopElapsed() {
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
}

onMounted(startElapsed)
onUnmounted(stopElapsed)

const elapsedLabel = computed(() => {
  const s = elapsedSeconds.value
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const rem = s % 60
  return `${m}m ${rem}s`
})

const etaLabel = computed(() => {
  if (!build.value?.estimatedCompletionAt || !isActive.value) return null
  const etaMs = new Date(build.value.estimatedCompletionAt).getTime() - Date.now()
  if (etaMs <= 0) return 'any moment'
  const s = Math.ceil(etaMs / 1000)
  if (s < 60) return `~${s}s`
  const m = Math.floor(s / 60)
  const rem = s % 60
  return rem > 0 ? `~${m}m ${rem}s` : `~${m}m`
})

function formatBytes(bytes?: number) {
  if (!bytes) return ''
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`
}
</script>

<template>
  <div class="max-w-4xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <div v-if="isLoading" class="space-y-4">
        <div class="h-8 bg-slate-900/60 rounded animate-pulse" />
        <div class="h-64 bg-slate-900/60 rounded animate-pulse" />
      </div>

      <div v-else-if="build" class="space-y-6">
        <!-- Header -->
        <div>
          <p class="text-[11px] font-mono tracking-[0.25em] uppercase text-slate-500 mb-1">
            Module // Build Status
          </p>
          <div class="flex items-center justify-between mb-2">
            <h1 class="text-2xl md:text-3xl font-semibold text-slate-50">Build status</h1>
            <BaseBadge v-if="stateInfo" :variant="stateInfo.color">
              {{ stateInfo.label }}
            </BaseBadge>
          </div>
          <div class="flex items-center gap-4 text-sm text-slate-400 flex-wrap">
            <span class="font-mono">{{ build.id }}</span>
            <span>•</span>
            <span>{{ build.templateName }}</span>
            <span>•</span>
            <span>Submitted {{ formatTimeAgo(build.submittedAt) }}</span>
          </div>
        </div>

        <!-- Scripts list -->
        <BaseCard v-if="build.scripts.length > 0" padding="md">
          <p class="text-xs font-mono tracking-widest uppercase text-slate-400 mb-3">Scripts</p>
          <div class="space-y-1">
            <div
              v-for="s in build.scripts"
              :key="s.scriptId"
              class="flex items-center gap-2 text-sm text-slate-300"
            >
              <span class="text-slate-600 font-mono w-5 text-right">{{ s.executionOrder }}.</span>
              <span>{{ s.scriptName }}</span>
            </div>
          </div>
        </BaseCard>

        <!-- State transition timestamps -->
        <BaseCard padding="md">
          <p class="text-xs font-mono tracking-widest uppercase text-slate-400 mb-3">Timeline</p>
          <dl class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 text-sm">
            <div class="flex justify-between gap-4">
              <dt class="text-slate-500">Submitted</dt>
              <dd class="text-slate-300 font-mono text-xs">{{ formatTimestamp(build.submittedAt) }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-slate-500">Claimed</dt>
              <dd class="text-slate-300 font-mono text-xs">{{ formatTimestamp(build.claimedAt) }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-slate-500">Building started</dt>
              <dd class="text-slate-300 font-mono text-xs">{{ formatTimestamp(build.buildingStartedAt) }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-slate-500">Exporting started</dt>
              <dd class="text-slate-300 font-mono text-xs">{{ formatTimestamp(build.exportingStartedAt) }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-slate-500">Completed</dt>
              <dd class="text-slate-300 font-mono text-xs">{{ formatTimestamp(build.completedAt) }}</dd>
            </div>
            <div class="flex justify-between gap-4">
              <dt class="text-slate-500">Failed</dt>
              <dd class="text-slate-300 font-mono text-xs">{{ formatTimestamp(build.failedAt) }}</dd>
            </div>
          </dl>
        </BaseCard>

        <!-- Active build progress -->
        <BaseCard v-if="isActive" padding="md" class="space-y-5">
          <!-- Header row -->
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div class="w-4 h-4 rounded-full border-2 border-primary-500 border-t-transparent animate-spin shrink-0" />
              <span class="text-sm text-slate-200 font-medium">Build in progress</span>
            </div>
            <div class="flex items-center gap-4 text-xs text-slate-500">
              <span>Elapsed: <span class="text-slate-300 font-mono">{{ elapsedLabel }}</span></span>
              <span v-if="etaLabel">ETA: <span class="text-slate-300 font-mono">{{ etaLabel }}</span></span>
              <span>{{ progressPercent }}%</span>
            </div>
          </div>

          <!-- Progress bar -->
          <BaseProgress :value="progressPercent" :show-label="false" />

          <!-- Current stage label -->
          <p class="text-xs font-mono text-slate-400">
            <template v-if="build?.currentWorkerStage">
              {{ PIPELINE_STAGES.find(s => s.key === build?.currentWorkerStage?.toUpperCase())?.label ?? build?.currentWorkerStage }}
            </template>
            <template v-else>
              Waiting for a worker to claim the job…
            </template>
          </p>

          <!-- Pipeline step list -->
          <div class="grid grid-cols-2 gap-x-6 gap-y-1.5 pt-1 border-t border-slate-800">
            <div
              v-for="(stage, idx) in PIPELINE_STAGES"
              :key="stage.key"
              class="flex items-center gap-2 text-xs"
              :class="{
                'text-primary-400': idx === currentStageIndex,
                'text-slate-500 line-through decoration-slate-700': idx < currentStageIndex,
                'text-slate-600': idx > currentStageIndex,
              }"
            >
              <span
                class="w-1.5 h-1.5 rounded-full shrink-0"
                :class="{
                  'bg-primary-400 animate-pulse': idx === currentStageIndex,
                  'bg-slate-600': idx < currentStageIndex,
                  'bg-slate-800': idx > currentStageIndex,
                }"
              />
              {{ stage.label }}
            </div>
          </div>
        </BaseCard>

        <!-- Ready state -->
        <BaseCard v-if="build.state === 'ready'" padding="lg">
          <div class="text-center mb-6">
            <h2 class="text-2xl font-bold text-slate-100 mb-2">Build Complete</h2>
            <p class="text-slate-400">Your vulnerable VM is ready for download.</p>
          </div>

          <div class="space-y-4">
            <div class="bg-slate-800/60 rounded-lg p-4">
              <div class="flex items-center justify-between mb-1">
                <span class="text-slate-300 font-mono text-sm">
                  {{ build.ovaFilename ?? `vm-${build.id}.ova` }}
                </span>
                <span class="text-sm text-slate-400">{{ formatBytes(build.ovaSizeBytes) }}</span>
              </div>
              <p v-if="build.ovaSha256" class="text-xs text-slate-600 font-mono truncate mb-2">
                SHA256: {{ build.ovaSha256 }}
              </p>
              <BaseButton class="w-full mt-2" :disabled="downloading" @click="downloadOva">
                {{ downloading ? `Downloading… ${downloadProgress}%` : 'Download OVA' }}
              </BaseButton>
              <p v-if="downloadError" class="text-xs text-red-400 mt-2">{{ downloadError }}</p>
            </div>

            <div class="rounded-lg border border-slate-700/50 bg-slate-800/30 px-4 py-3 text-xs text-slate-400 space-y-1">
              <p class="font-medium text-slate-300">Trust boundary notice</p>
              <p>Once downloaded, this VM runs outside platform control. The platform cannot enforce or verify runtime integrity post-export. To independently verify the artefact, recompute its SHA-256 and compare it against the provenance manifest.</p>
            </div>

            <div class="bg-slate-800/60 rounded-lg p-4">
              <span class="text-slate-300 font-mono text-sm">provenance-{{ build.id }}.json</span>
              <BaseButton variant="secondary" class="w-full mt-2" :disabled="downloadingProvenance" @click="downloadManifest">
                {{ downloadingProvenance ? 'Downloading…' : 'Download Manifest' }}
              </BaseButton>
            </div>
          </div>

          <!-- Telemetry upload (only when telemetry was enabled at build time) -->
          <div v-if="build.telemetryEnabled" class="mt-6 pt-6 border-t border-slate-800">
            <p class="text-xs font-mono tracking-widest uppercase text-slate-400 mb-3">Session Telemetry</p>

            <div v-if="telemetryDeleted" class="bg-slate-800/60 border border-slate-700/50 rounded-lg p-4 text-sm text-slate-400">
              Telemetry has been deleted.
            </div>

            <div v-else-if="telemetrySubmitted" class="space-y-3">
              <div class="bg-emerald-500/10 border border-emerald-500/30 rounded-lg p-4 text-sm text-emerald-300">
                Telemetry submitted - your instructor can now review your session.
              </div>
              <div v-if="telemetryDeleteError" class="text-xs text-red-400">{{ telemetryDeleteError }}</div>
              <BaseButton variant="secondary" class="w-full" :disabled="telemetryDeleting" @click="deleteTelemetry">
                {{ telemetryDeleting ? 'Deleting…' : 'Delete Telemetry' }}
              </BaseButton>
            </div>

            <div v-else class="space-y-3">
              <p class="text-xs text-slate-500">
                Run <code class="bg-slate-800 px-1 rounded">collect-telemetry</code> inside the VM, then upload
                <code class="bg-slate-800 px-1 rounded">/tmp/vmgen-telemetry.json</code> here.
              </p>
              <div class="rounded-lg border border-slate-700/50 bg-slate-800/30 px-3 py-2 text-xs text-slate-400">
                Telemetry is self-reported by the guest VM. The platform cannot verify its authenticity or completeness.
              </div>
              <input
                type="file"
                accept=".json,application/json"
                class="block w-full text-sm text-slate-400 file:mr-3 file:py-1.5 file:px-3 file:rounded-lg file:border file:border-slate-600 file:text-xs file:font-medium file:text-slate-300 file:bg-slate-800 hover:file:bg-slate-700 file:cursor-pointer cursor-pointer"
                @change="onTelemetryFileChange"
              />
              <div v-if="telemetryError" class="text-xs text-red-400">{{ telemetryError }}</div>
              <BaseButton
                variant="secondary"
                class="w-full"
                :disabled="!telemetryFile || telemetryUploading"
                @click="submitTelemetry"
              >
                {{ telemetryUploading ? 'Uploading…' : 'Upload Telemetry' }}
              </BaseButton>
            </div>
          </div>

          <div class="mt-6 pt-6 border-t border-slate-800 space-y-4">
            <p class="text-xs font-mono tracking-widest uppercase text-slate-400">Submit Flag</p>

            <div v-if="allFlagsCorrect" class="bg-emerald-500/10 border border-emerald-500/30 rounded-lg p-4 text-sm text-emerald-300">
              {{ build.scripts.length === 1 ? 'Correct flag - challenge complete.' : 'All flags correct - challenge complete.' }}
            </div>

            <div v-if="flagExhausted && !allFlagsCorrect" class="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-sm text-red-400">
              No attempts remaining for this build.
            </div>

            <div
              v-for="s in build.scripts"
              v-show="!allFlagsCorrect || flagStates[s.scriptId]?.correct"
              :key="s.scriptId"
              class="space-y-2"
            >
              <p v-if="build.scripts.length > 1" class="text-xs text-slate-500 font-mono">
                {{ s.scriptName }}
                <span v-if="flagStates[s.scriptId]?.correct" class="text-emerald-400 ml-1">✓</span>
              </p>

              <div v-if="flagStates[s.scriptId]?.correct" class="text-sm text-emerald-400">
                Correct flag accepted.
              </div>

              <template v-else-if="!flagExhausted">
                <div class="flex gap-2">
                  <input
                    v-model="flagStates[s.scriptId]!.input"
                    type="text"
                    placeholder="FLAG{…}"
                    class="flex-1 bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 font-mono placeholder-slate-600 focus:outline-none focus:border-primary-500 disabled:opacity-50 disabled:cursor-not-allowed"
                    :disabled="flagStates[s.scriptId]?.submitting"
                    @keyup.enter="submitFlag(s.scriptId)"
                  />
                  <BaseButton
                    variant="primary"
                    :disabled="!flagStates[s.scriptId]?.input.trim() || flagStates[s.scriptId]?.submitting"
                    @click="submitFlag(s.scriptId)"
                  >
                    {{ flagStates[s.scriptId]?.submitting ? 'Checking…' : 'Submit' }}
                  </BaseButton>
                </div>
                <div v-if="flagStates[s.scriptId]?.error" class="text-sm text-red-400">
                  {{ flagStates[s.scriptId]?.error }}
                </div>
              </template>
            </div>
          </div>
        </BaseCard>

        <!-- Archived state -->
        <BaseCard v-if="build.state === 'archived'" padding="lg">
          <div class="text-center mb-6">
            <h2 class="text-2xl font-bold text-slate-300 mb-2">Build Archived</h2>
            <p class="text-slate-400">
              This build has been archived. The OVA file is no longer available for download.
              Provenance records and flag submission remain accessible.
            </p>
          </div>
          <div class="space-y-3">
            <BaseButton variant="secondary" class="w-full" :disabled="downloadingProvenance" @click="downloadManifest">
              {{ downloadingProvenance ? 'Downloading…' : 'Download Provenance Manifest' }}
            </BaseButton>
          </div>
        </BaseCard>

        <!-- Failed state -->
        <BaseCard v-if="build.state === 'failed'" padding="lg">
          <div class="text-center mb-6">
            <h2 class="text-2xl font-bold text-danger-500 mb-2">Build Failed</h2>
            <p v-if="build.failureReason" class="text-slate-400 font-mono text-sm bg-slate-800/60 p-4 rounded">
              {{ build.failureReason }}
            </p>
          </div>
          <div class="space-y-2">
            <BaseButton class="w-full" :disabled="cloning" @click="retryBuild">
              {{ cloning ? 'Retrying…' : 'Retry Build' }}
            </BaseButton>
            <p v-if="cloneError" class="text-sm text-red-400 text-center">{{ cloneError }}</p>
          </div>
        </BaseCard>

        <!-- Build logs (all states) -->
        <BaseCard padding="md">
          <button
            class="flex items-center justify-between w-full text-left"
            @click="toggleLogs"
          >
            <p class="text-xs font-mono tracking-widest uppercase text-slate-400">Build Logs</p>
            <span class="text-slate-500 text-xs">{{ logsVisible ? '▲ hide' : '▼ show' }}</span>
          </button>

          <div v-if="logsVisible" class="mt-4">
            <div v-if="logsLoading" class="text-xs text-slate-500">Loading…</div>
            <div v-else-if="logsError" class="text-xs text-red-400">{{ logsError }}</div>
            <div v-else-if="buildLogs.length === 0" class="text-xs text-slate-500">No logs yet.</div>
            <div v-else class="space-y-0.5 max-h-80 overflow-y-auto font-mono text-xs">
              <div
                v-for="entry in buildLogs"
                :key="entry.id"
                class="flex gap-2 leading-5"
              >
                <span class="text-slate-600 shrink-0">{{ entry.loggedAt.slice(11, 19) }}</span>
                <span
                  class="shrink-0 w-12"
                  :class="logLevelClass[entry.logLevel] ?? 'text-slate-400'"
                >{{ entry.logLevel }}</span>
                <span v-if="entry.stage" class="text-slate-500 shrink-0">[{{ entry.stage }}]</span>
                <span class="text-slate-300 break-all">{{ entry.message }}</span>
              </div>
            </div>
          </div>
        </BaseCard>
      </div>

      <div v-else class="text-center py-12">
        <p class="text-slate-400">Build not found</p>
      </div>
    </div>
  </div>
</template>
