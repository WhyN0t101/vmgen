<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Plus, X } from 'lucide-vue-next'
import { useRegisterTemplate, useProxmoxVms } from '@/composables/useTemplates'
import { api } from '@/api/endpoints'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import type { RegisterTemplateRequest } from '@/types'

const router = useRouter()
const registerMutation = useRegisterTemplate()

// Form state
const form = ref<RegisterTemplateRequest>({
  identifier: '',
  version: '',
  osFamily: 'linux',
  osVersion: '',
  architecture: 'x64',
  description: '',
  proxmoxVmid: 0,
  proxmoxNode: 'pve',
  diskStorage: 'local-lvm',
  diskSizeGb: 20,
  minRamMb: 2048,
  minCpuCores: 2,
  expectedSshUser: 'root',
  expectedSshPort: 22,
  networkBridge: 'vmbr0',
  capabilities: [],
})

const customCapability = ref('')
const { data: proxmoxVms, refetch: fetchVms } = useProxmoxVms(() => form.value.proxmoxNode)

const commonCapabilities = ['ssh', 'web_server', 'database', 'docker', 'systemd']

function toggleCapability(cap: string) {
  const index = form.value.capabilities.indexOf(cap)
  if (index > -1) {
    form.value.capabilities.splice(index, 1)
  } else {
    form.value.capabilities.push(cap)
  }
}

function addCustomCapability() {
  if (customCapability.value && !form.value.capabilities.includes(customCapability.value)) {
    form.value.capabilities.push(customCapability.value)
    customCapability.value = ''
  }
}

function removeCapability(cap: string) {
  const index = form.value.capabilities.indexOf(cap)
  if (index > -1) {
    form.value.capabilities.splice(index, 1)
  }
}

async function selectVm(vm: any) {
  if (!vm) return
  form.value.proxmoxVmid = vm.vmid
  
  // Auto-populate storage from VM config
  try {
    const storageResponse = await api.admin.getVmStorage(vm.vmid, form.value.proxmoxNode)
    if (storageResponse.storage) {
      form.value.diskStorage = storageResponse.storage
    }
  } catch (error) {
    console.warn('Could not auto-detect storage for VM:', error)
    // Keep current value or default
  }
}

async function handleSubmit() {
  try {
    const result = await registerMutation.mutateAsync(form.value)
    router.push({ name: 'admin-template-detail', params: { id: result.id } })
  } catch (error) {
    // Error handled by mutation
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
      <h1 class="text-3xl font-bold text-slate-100">Register Template</h1>
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-6">
      <!-- Basic Information -->
      <BaseCard>
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Basic Information</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">
              Identifier <span class="text-red-400">*</span>
            </label>
            <input
              v-model="form.identifier"
              type="text"
              required
              pattern="[a-z0-9\-]+"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="ubuntu-server"
            />
            <p class="mt-1 text-xs text-slate-400">Lowercase letters, numbers, hyphens only</p>
          </div>

          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">
              Version <span class="text-red-400">*</span>
            </label>
            <input
              v-model="form.version"
              type="text"
              required
              pattern="v\d+\.\d+\.\d+"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="v1.0.0"
            />
            <p class="mt-1 text-xs text-slate-400">Format: vX.Y.Z</p>
          </div>

          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Description</label>
            <textarea
              v-model="form.description"
              rows="3"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>
      </BaseCard>

      <!-- Proxmox Reference -->
      <BaseCard>
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Proxmox Reference</h2>
        <div class="space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">Node</label>
              <input
                v-model="form.proxmoxNode"
                type="text"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
            <div>
              <label class="block text-sm font-medium text-slate-300 mb-2">
                VMID <span class="text-red-400">*</span>
              </label>
              <input
                v-model.number="form.proxmoxVmid"
                type="number"
                required
                min="100"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>
          </div>

          <div>
            <BaseButton type="button" variant="secondary" @click="fetchVms()">
              Fetch Available VMs
            </BaseButton>
            <div v-if="proxmoxVms && proxmoxVms.length > 0" class="mt-4">
              <select
                @change="selectVm(proxmoxVms.find((v: any) => v.vmid === Number($event.target.value)))"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                <option value="">Select a VM...</option>
                <option v-for="vm in proxmoxVms" :key="vm.vmid" :value="vm.vmid">
                  {{ vm.name }} ({{ vm.vmid }}) - {{ vm.status }}{{ vm.isTemplate ? ' [Proxmox Template]' : '' }}
                </option>
              </select>
              <p class="mt-2 text-xs text-slate-400">
                <span class="text-yellow-400">Note:</span> Items marked "[Proxmox Template]" are Proxmox templates. 
                You can register them, but you may need to clone them to a VM first for full functionality.
              </p>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Disk Storage</label>
            <input
              v-model="form.diskStorage"
              type="text"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>
      </BaseCard>

      <!-- Operating System -->
      <BaseCard>
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Operating System</h2>
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">OS Family</label>
            <div class="flex gap-4">
              <label class="flex items-center">
                <input v-model="form.osFamily" type="radio" value="linux" class="mr-2" />
                <span class="text-slate-300">Linux</span>
              </label>
              <label class="flex items-center">
                <input v-model="form.osFamily" type="radio" value="windows" class="mr-2" />
                <span class="text-slate-300">Windows</span>
              </label>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">
              OS Version <span class="text-red-400">*</span>
            </label>
            <input
              v-model="form.osVersion"
              type="text"
              required
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="20.04"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Architecture</label>
            <div class="flex gap-4">
              <label class="flex items-center">
                <input v-model="form.architecture" type="radio" value="x64" class="mr-2" />
                <span class="text-slate-300">x64</span>
              </label>
              <label class="flex items-center">
                <input v-model="form.architecture" type="radio" value="arm64" class="mr-2" />
                <span class="text-slate-300">arm64</span>
              </label>
            </div>
          </div>
        </div>
      </BaseCard>

      <!-- Resources -->
      <BaseCard>
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Resources</h2>
        <div class="grid grid-cols-3 gap-4">
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Disk Size (GB)</label>
            <input
              v-model.number="form.diskSizeGb"
              type="number"
              min="1"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Min RAM (MB)</label>
            <input
              v-model.number="form.minRamMb"
              type="number"
              min="512"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Min CPU Cores</label>
            <input
              v-model.number="form.minCpuCores"
              type="number"
              min="1"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>
      </BaseCard>

      <!-- Validation Configuration -->
      <BaseCard>
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Validation Configuration</h2>
        <div class="grid grid-cols-3 gap-4">
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Expected SSH User</label>
            <input
              v-model="form.expectedSshUser"
              type="text"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Expected SSH Port</label>
            <input
              v-model.number="form.expectedSshPort"
              type="number"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">Network Bridge</label>
            <input
              v-model="form.networkBridge"
              type="text"
              class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="vmbr1"
            />
          </div>
        </div>
      </BaseCard>

      <!-- Capabilities -->
      <BaseCard>
        <h2 class="text-xl font-semibold text-slate-100 mb-4">Capabilities</h2>
        <div class="space-y-4">
          <div class="flex flex-wrap gap-2">
            <label
              v-for="cap in commonCapabilities"
              :key="cap"
              class="flex items-center px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg cursor-pointer hover:bg-slate-700 transition-colors"
            >
              <input
                type="checkbox"
                :checked="form.capabilities.includes(cap)"
                @change="toggleCapability(cap)"
                class="mr-2"
              />
              <span class="text-slate-300">{{ cap }}</span>
            </label>
          </div>

          <div class="flex gap-2">
            <input
              v-model="customCapability"
              type="text"
              placeholder="Custom capability..."
              class="flex-1 px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              @keyup.enter="addCustomCapability"
            />
            <BaseButton type="button" variant="secondary" @click="addCustomCapability">
              <Plus class="w-4 h-4 mr-2" />
              Add
            </BaseButton>
          </div>

          <div v-if="form.capabilities.length > 0" class="flex flex-wrap gap-2">
            <span
              v-for="cap in form.capabilities"
              :key="cap"
              class="inline-flex items-center gap-1 px-3 py-1 bg-primary-500/20 text-primary-400 rounded-md text-sm"
            >
              {{ cap }}
              <button
                type="button"
                @click="removeCapability(cap)"
                class="hover:text-primary-300"
              >
                <X class="w-3 h-3" />
              </button>
            </span>
          </div>
        </div>
      </BaseCard>

      <!-- Submit -->
      <div class="flex justify-end gap-4">
        <BaseButton type="button" variant="ghost" @click="router.back()">
          Cancel
        </BaseButton>
        <BaseButton type="submit" :loading="registerMutation.isPending.value">
          {{ registerMutation.isPending.value ? 'Registering...' : 'Register Template' }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>
