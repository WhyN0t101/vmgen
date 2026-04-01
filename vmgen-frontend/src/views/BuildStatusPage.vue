<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useBuildStatus } from '@/composables/useBuildStatus'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseBadge from '@/components/ui/BaseBadge.vue'
import { formatTimeAgoVerbose as formatTimeAgo } from '@/utils/dateFormat'
import { api } from '@/api/endpoints'

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
        <BaseCard v-if="isActive" padding="md">
          <div class="flex items-center gap-3 mb-3">
            <div class="w-4 h-4 rounded-full border-2 border-primary-500 border-t-transparent animate-spin" />
            <span class="text-sm text-slate-300 font-medium">Build in progress…</span>
          </div>
          <p v-if="build.currentWorkerStage" class="text-xs text-slate-500 font-mono">
            {{ build.currentWorkerStage }}
          </p>
          <p v-else class="text-xs text-slate-500">Waiting for worker to pick up the job</p>
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
        </BaseCard>
      </div>

      <div v-else class="text-center py-12">
        <p class="text-slate-400">Build not found</p>
      </div>
    </div>
  </div>
</template>
