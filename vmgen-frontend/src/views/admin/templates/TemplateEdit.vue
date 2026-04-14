<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { useTemplate, useUpdateTemplateMetadata } from '@/composables/useTemplates'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import type { UpdateTemplateMetadataRequest } from '@/types'

const route = useRoute()
const router = useRouter()
const templateId = computed(() => route.params.id as string)

const { data: template, isLoading } = useTemplate(templateId)
const updateMutation = useUpdateTemplateMetadata()

const form = ref<UpdateTemplateMetadataRequest>({
  description: '',
  expectedSshUser: '',
  expectedSshPort: 22,
  networkBridge: '',
  minRamMb: 2048,
  minCpuCores: 2,
})

// Populate form when template loads
watch(template, (t) => {
  if (t) {
    form.value = {
      description: t.description || '',
      expectedSshUser: t.expectedSshUser,
      expectedSshPort: t.expectedSshPort,
      networkBridge: t.networkBridge || '',
      minRamMb: t.minRamMb,
      minCpuCores: t.minCpuCores,
    }
  }
}, { immediate: true })

async function handleSubmit() {
  try {
    await updateMutation.mutateAsync({ id: templateId.value, data: form.value })
    router.push({ name: 'admin-template-detail', params: { id: templateId.value } })
  } catch (error) {
    // Error handled by mutation via toast; keep user on the form.
  }
}
</script>

<template>
  <div class="container mx-auto px-6 py-8 max-w-3xl">
    <div class="flex items-center gap-4 mb-6">
      <BaseButton variant="ghost" size="sm" @click="router.back()">
        <ArrowLeft class="w-4 h-4 mr-2" />
        Back
      </BaseButton>
      <h1 class="text-3xl font-bold text-slate-100">Edit Template Metadata</h1>
    </div>

    <div v-if="isLoading" class="text-center py-12 text-slate-400">
      Loading template...
    </div>

    <form v-else-if="template" @submit.prevent="handleSubmit" class="space-y-6">
      <!-- Read-only fields -->
      <BaseCard>
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Immutable Fields</h2>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-slate-400 mb-2">Identifier</label>
            <input
              :value="template.identifier"
              type="text"
              disabled
              class="w-full px-4 py-2 bg-slate-900 border border-slate-700 rounded-lg text-slate-500 cursor-not-allowed"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-400 mb-2">Version</label>
            <input
              :value="template.version"
              type="text"
              disabled
              class="w-full px-4 py-2 bg-slate-900 border border-slate-700 rounded-lg text-slate-500 cursor-not-allowed"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-400 mb-2">Proxmox VMID</label>
            <input
              :value="template.proxmoxVmid"
              type="number"
              disabled
              class="w-full px-4 py-2 bg-slate-900 border border-slate-700 rounded-lg text-slate-500 cursor-not-allowed"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-400 mb-2">Proxmox Node</label>
            <input
              :value="template.proxmoxNode"
              type="text"
              disabled
              class="w-full px-4 py-2 bg-slate-900 border border-slate-700 rounded-lg text-slate-500 cursor-not-allowed"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-400 mb-2">OS Family</label>
            <input
              :value="template.osFamily"
              type="text"
              disabled
              class="w-full px-4 py-2 bg-slate-900 border border-slate-700 rounded-lg text-slate-500 cursor-not-allowed"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-400 mb-2">OS Version</label>
            <input
              :value="template.osVersion"
              type="text"
              disabled
              class="w-full px-4 py-2 bg-slate-900 border border-slate-700 rounded-lg text-slate-500 cursor-not-allowed"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-400 mb-2">Architecture</label>
            <input
              :value="template.architecture"
              type="text"
              disabled
              class="w-full px-4 py-2 bg-slate-900 border border-slate-700 rounded-lg text-slate-500 cursor-not-allowed"
            />
          </div>
        </div>
      </BaseCard>

      <!-- Editable fields -->
      <BaseCard>
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Editable Fields</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Description</label>
            <textarea
              v-model="form.description"
              rows="3"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>

          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">Expected SSH User</label>
              <input
                v-model="form.expectedSshUser"
                type="text"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
              <p class="mt-1 text-[11px] text-slate-500">
                Username the worker will use for guest SSH checks and provisioning.
              </p>
            </div>
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">Expected SSH Port</label>
              <input
                v-model.number="form.expectedSshPort"
                type="number"
                min="1"
                max="65535"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
              <p class="mt-1 text-[11px] text-slate-500">
                Port the worker expects SSH to be listening on inside the template.
              </p>
            </div>
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">Network Bridge</label>
              <input
                v-model="form.networkBridge"
                type="text"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
              <p class="mt-1 text-[11px] text-slate-500">
                Proxmox bridge used for build VMs (e.g. the isolated build bridge).
              </p>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">Min RAM (MB)</label>
              <input
                v-model.number="form.minRamMb"
                type="number"
                min="512"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
              <p class="mt-1 text-[11px] text-slate-500">
                Compatibility hint used to avoid applying scripts that require more memory.
              </p>
            </div>
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">Min CPU Cores</label>
              <input
                v-model.number="form.minCpuCores"
                type="number"
                min="1"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
              <p class="mt-1 text-[11px] text-slate-500">
                Compatibility hint for scripts that need additional CPU resources.
              </p>
            </div>
          </div>
        </div>
      </BaseCard>

      <div class="flex justify-end gap-4">
        <BaseButton type="button" variant="ghost" @click="router.back()">
          Cancel
        </BaseButton>
        <BaseButton type="submit" :loading="updateMutation.isPending.value">
          {{ updateMutation.isPending.value ? 'Updating...' : 'Update Metadata' }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>

