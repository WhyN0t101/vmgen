<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CheckCircle, Edit, XCircle } from 'lucide-vue-next'
import { useTemplate, useValidateTemplate, useDeprecateTemplate } from '@/composables/useTemplates'
import TemplateStatusBadge from '@/components/templates/TemplateStatusBadge.vue'
import TemplateCapabilityBadge from '@/components/templates/TemplateCapabilityBadge.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'

const route = useRoute()
const router = useRouter()
const templateId = computed(() => route.params.id as string)

const { data: template, isLoading } = useTemplate(templateId)
const validateMutation = useValidateTemplate()
const deprecateMutation = useDeprecateTemplate()

function formatDate(dateString?: string) {
  if (!dateString) return 'N/A'
  return new Date(dateString).toLocaleString()
}

const parsedValidationLogs = computed(() => {
  if (!template.value?.validationLogs) return ''
  try {
    const parsed = typeof template.value.validationLogs === 'string' 
      ? JSON.parse(template.value.validationLogs)
      : template.value.validationLogs
    return JSON.stringify(parsed, null, 2)
  } catch (e) {
    return template.value.validationLogs
  }
})

async function handleValidate() {
  if (!confirm('Are you sure you want to validate this template?')) return
  try {
    await validateMutation.mutateAsync(templateId.value)
  } catch (error) {
    // Error handled by mutation
  }
}

async function handleDeprecate() {
  if (!confirm('Are you sure you want to deprecate this template? This action cannot be undone.')) return
  try {
    await deprecateMutation.mutateAsync(templateId.value)
    router.push({ name: 'admin-template-list' })
  } catch (error) {
    // Error handled by mutation
  }
}
</script>

<template>
  <div class="container mx-auto px-6 py-8">
    <div v-if="isLoading" class="text-center py-12 text-slate-400">
      Loading template...
    </div>

    <div v-else-if="template">
      <!-- Header -->
      <div class="flex items-center gap-4 mb-6">
        <BaseButton variant="ghost" size="sm" @click="router.back()">
          <ArrowLeft class="w-4 h-4 mr-2" />
          Back
        </BaseButton>
        <div class="flex-1">
          <h1 class="text-3xl font-bold text-slate-100">
            {{ template.identifier }} {{ template.version }}
          </h1>
          <div class="mt-2">
            <TemplateStatusBadge :status="template.status" />
          </div>
        </div>
        <div class="flex gap-2">
          <BaseButton
            v-if="template.status === 'draft'"
            variant="secondary"
            @click="handleValidate"
            :loading="validateMutation.isPending.value"
          >
            <CheckCircle class="w-4 h-4 mr-2" />
            Validate
          </BaseButton>
          <BaseButton
            variant="ghost"
            @click="router.push({ name: 'admin-template-edit', params: { id: template.id } })"
          >
            <Edit class="w-4 h-4 mr-2" />
            Edit Metadata
          </BaseButton>
          <BaseButton
            v-if="template.status !== 'deprecated'"
            variant="danger"
            @click="handleDeprecate"
            :loading="deprecateMutation.isPending.value"
          >
            <XCircle class="w-4 h-4 mr-2" />
            Deprecate
          </BaseButton>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Basic Information -->
        <BaseCard>
          <h2 class="text-xl font-semibold text-slate-100 mb-4">Basic Information</h2>
          <dl class="space-y-3">
            <div>
              <dt class="text-sm text-slate-400">OS</dt>
              <dd class="text-slate-100 capitalize">
                {{ template.osFamily }} {{ template.osVersion }} ({{ template.architecture }})
              </dd>
            </div>
            <div v-if="template.description">
              <dt class="text-sm text-slate-400">Description</dt>
              <dd class="text-slate-100">{{ template.description }}</dd>
            </div>
            <div>
              <dt class="text-sm text-slate-400">Created</dt>
              <dd class="text-slate-100">{{ formatDate(template.createdAt) }}</dd>
            </div>
            <div v-if="template.deprecatedAt">
              <dt class="text-sm text-slate-400">Deprecated</dt>
              <dd class="text-slate-100">{{ formatDate(template.deprecatedAt) }}</dd>
            </div>
          </dl>
        </BaseCard>

        <!-- Proxmox Configuration -->
        <BaseCard>
          <h2 class="text-xl font-semibold text-slate-100 mb-4">Proxmox Configuration</h2>
          <dl class="space-y-3">
            <div>
              <dt class="text-sm text-slate-400">VMID</dt>
              <dd class="text-slate-100">{{ template.proxmoxVmid }}</dd>
            </div>
            <div>
              <dt class="text-sm text-slate-400">Node</dt>
              <dd class="text-slate-100">{{ template.proxmoxNode }}</dd>
            </div>
            <div>
              <dt class="text-sm text-slate-400">Storage</dt>
              <dd class="text-slate-100">{{ template.diskStorage }}</dd>
            </div>
            <div v-if="template.networkBridge">
              <dt class="text-sm text-slate-400">Network Bridge</dt>
              <dd class="text-slate-100">{{ template.networkBridge }}</dd>
            </div>
            <div>
              <dt class="text-sm text-slate-400">SSH</dt>
              <dd class="text-slate-100">{{ template.expectedSshUser }}@{{ template.expectedSshPort }}</dd>
            </div>
          </dl>
        </BaseCard>

        <!-- Resources -->
        <BaseCard>
          <h2 class="text-xl font-semibold text-slate-100 mb-4">Resources</h2>
          <dl class="space-y-3">
            <div>
              <dt class="text-sm text-slate-400">Disk Size</dt>
              <dd class="text-slate-100">{{ template.diskSizeGb }} GB</dd>
            </div>
            <div>
              <dt class="text-sm text-slate-400">Min RAM</dt>
              <dd class="text-slate-100">{{ template.minRamMb }} MB</dd>
            </div>
            <div>
              <dt class="text-sm text-slate-400">Min CPU Cores</dt>
              <dd class="text-slate-100">{{ template.minCpuCores }}</dd>
            </div>
          </dl>
        </BaseCard>

        <!-- Capabilities -->
        <BaseCard>
          <h2 class="text-xl font-semibold text-slate-100 mb-4">Capabilities</h2>
          <div v-if="template.capabilities.length === 0" class="text-slate-400 text-sm">
            No capabilities defined
          </div>
          <div v-else class="flex flex-wrap gap-2">
            <TemplateCapabilityBadge
              v-for="cap in template.capabilities"
              :key="cap"
              :capability="cap"
            />
          </div>
        </BaseCard>
      </div>

      <!-- Validation Info -->
      <BaseCard v-if="template.validatedAt" class="mt-6">
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Validation</h2>
        <dl class="space-y-3">
          <div>
            <dt class="text-sm text-slate-400">Validated At</dt>
            <dd class="text-slate-100">{{ formatDate(template.validatedAt) }}</dd>
          </div>
          <div v-if="template.sha256Hash">
            <dt class="text-sm text-slate-400">SHA256 Hash</dt>
            <dd class="text-slate-100 font-mono text-xs break-all">{{ template.sha256Hash }}</dd>
          </div>
          <div v-if="template.validationLogs">
            <dt class="text-sm text-slate-400 mb-2">Validation Logs</dt>
            <dd class="text-slate-100">
              <pre class="bg-slate-900 p-4 rounded-lg overflow-auto text-xs">{{ parsedValidationLogs }}</pre>
            </dd>
          </div>
        </dl>
      </BaseCard>
    </div>
  </div>
</template>
