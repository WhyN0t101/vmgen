<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBuildStatus } from '@/composables/useBuildStatus'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import BaseProgress from '@/components/ui/BaseProgress.vue'
import { formatTimeAgoVerbose as formatTimeAgo } from '@/utils/dateFormat'
import { api } from '@/api/endpoints'

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
  { key: 'EXPORT_OVA',          label: 'Export OVA',           percent: 87 },
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
const downloadingProvenance = ref(false)

// Flag submission
const flagInput = ref('')
const flagSubmitting = ref(false)
const flagResult = ref<{ correct: boolean; attemptsRemaining: number } | null>(null)
const flagError = ref<string | null>(null)

async function submitFlag() {
  if (!build.value || !flagInput.value.trim()) return
  flagSubmitting.value = true
  flagResult.value = null
  flagError.value = null
  try {
    flagResult.value = await api.submitFlag(build.value.id, flagInput.value.trim())
  } catch (e: any) {
    flagError.value = e?.response?.data?.message ?? e?.message ?? 'Submission failed'
  } finally {
    flagSubmitting.value = false
  }
}

// Telemetry upload
const telemetryFile = ref<File | null>(null)
const telemetryUploading = ref(false)
const telemetrySubmitted = ref(false)
const telemetryError = ref<string | null>(null)

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
  try {
    const filename = build.value.ovaFilename ?? `vm-${build.value.id}.ova`
    await api.downloadBuild(build.value.id, filename)
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
              <span>{{ progressPercent }}%</span>
            </div>
          </div>

          <!-- Progress bar -->
          <BaseProgress :value="progressPercent" :show-label="false" />

          <!-- Current stage label -->
          <p class="text-xs font-mono text-slate-400">
            <template v-if="build.currentWorkerStage">
              {{ PIPELINE_STAGES.find(s => s.key === build.currentWorkerStage?.toUpperCase())?.label ?? build.currentWorkerStage }}
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
                {{ downloading ? 'Downloading…' : 'Download OVA' }}
              </BaseButton>
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

            <div v-if="telemetrySubmitted" class="bg-emerald-500/10 border border-emerald-500/30 rounded-lg p-4 text-sm text-emerald-300">
              Telemetry submitted — your instructor can now review your session.
            </div>

            <div v-else class="space-y-3">
              <p class="text-xs text-slate-500">
                Run <code class="bg-slate-800 px-1 rounded">collect-telemetry</code> inside the VM, then upload
                <code class="bg-slate-800 px-1 rounded">/tmp/vmgen-telemetry.json</code> here.
              </p>
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

          <div class="mt-6 pt-6 border-t border-slate-800 space-y-3">
            <p class="text-xs font-mono tracking-widest uppercase text-slate-400">Submit Flag</p>

            <div v-if="flagResult?.correct" class="bg-emerald-500/10 border border-emerald-500/30 rounded-lg p-4 text-sm text-emerald-300">
              Correct flag — challenge complete.
            </div>

            <template v-else>
              <div class="flex gap-2">
                <input
                  v-model="flagInput"
                  type="text"
                  placeholder="FLAG{…}"
                  class="flex-1 bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-slate-200 font-mono placeholder-slate-600 focus:outline-none focus:border-primary-500"
                  :disabled="flagSubmitting"
                  @keyup.enter="submitFlag"
                />
                <BaseButton
                  variant="primary"
                  :disabled="!flagInput.trim() || flagSubmitting"
                  @click="submitFlag"
                >
                  {{ flagSubmitting ? 'Checking…' : 'Submit' }}
                </BaseButton>
              </div>

              <div v-if="flagResult && !flagResult.correct" class="text-sm text-red-400">
                Incorrect. {{ flagResult.attemptsRemaining }} attempt{{ flagResult.attemptsRemaining === 1 ? '' : 's' }} remaining.
              </div>
              <div v-if="flagError" class="text-sm text-red-400">{{ flagError }}</div>
            </template>
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
      </div>

      <div v-else class="text-center py-12">
        <p class="text-slate-400">Build not found</p>
      </div>
    </div>
  </div>
</template>
