<script setup lang="ts">
import { ref, computed } from 'vue'
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
  expectedSshUser: 'vmgen-builder',
  expectedSshPort: 22,
  networkBridge: 'vmbr0',
  sshAuthMethod: 'cloudinit',
  capabilities: [],
})

const customCapability = ref('')
const { data: proxmoxVms, refetch: fetchVms } = useProxmoxVms(() => form.value.proxmoxNode)

// Only expose actual Proxmox templates in the dropdown; regular VMs remain selectable via manual VMID.
const proxmoxTemplates = computed(() =>
  (proxmoxVms.value || []).filter((vm: any) => vm.isTemplate)
)

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

const testingConnection = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

async function selectVm(vm: any) {
  if (!vm) return
  form.value.proxmoxVmid = vm.vmid

  // Suggest identifier based on VM name if not already set
  if (!form.value.identifier && vm.name) {
    form.value.identifier = String(vm.name)
      .toLowerCase()
      .replace(/[^a-z0-9-]+/g, '-')
      .replace(/^-+|-+$/g, '')
  }

  // Auto-populate basic resource hints from Proxmox summary
  if (typeof vm.maxmem === 'number' && vm.maxmem > 0) {
    // maxmem is bytes -> MB
    form.value.minRamMb = Math.max(512, Math.round(vm.maxmem / (1024 * 1024)))
  }
  if (typeof vm.cpus === 'number' && vm.cpus > 0) {
    form.value.minCpuCores = vm.cpus
  }

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

async function testConnection() {
  if (!form.value.proxmoxVmid) {
    testResult.value = {
      success: false,
      message: 'Please select a Proxmox template and VMID first.',
    }
    return
  }

  testingConnection.value = true
  testResult.value = null

  try {
    // Placeholder: backend/worker SSH validation is not yet implemented.
    // This keeps the UI flow ready while avoiding failing API calls.
    await new Promise(resolve => setTimeout(resolve, 800))
    testResult.value = {
      success: false,
      message: 'Backend SSH test endpoint is not implemented yet. This is a planned feature.',
    }
  } finally {
    testingConnection.value = false
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
            <div v-if="proxmoxTemplates && proxmoxTemplates.length > 0" class="mt-4">
              <select
                @change="selectVm(proxmoxTemplates.find((v: any) => v.vmid === Number($event.target.value)))"
                class="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                <option value="">Select a VM...</option>
                <option v-for="vm in proxmoxTemplates" :key="vm.vmid" :value="vm.vmid">
                  {{ vm.name }} ({{ vm.vmid }}) - {{ vm.status }}{{ vm.isTemplate ? ' [Proxmox Template]' : '' }}
                </option>
              </select>
              <p class="mt-2 text-xs text-slate-400">
                <span class="text-yellow-400">Note:</span> Only Proxmox templates are listed here.
                To reference a regular VM, enter its VMID manually above.
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

        <div class="mb-4 rounded-lg border border-slate-700 bg-slate-900/40 p-4">
          <p class="text-sm text-slate-200 font-semibold mb-1">What is validation?</p>
          <p class="text-xs text-slate-400">
            After registration, VMGen will boot this template in isolation, attempt SSH using the
            configuration below, and run basic checks. Templates must pass validation before users
            can request VMs from them.
          </p>
        </div>

        <div class="mb-4 space-y-2">
          <p class="text-sm font-medium text-slate-300">SSH Authentication Method</p>
          <div class="flex flex-col gap-1">
            <label class="inline-flex items-center gap-2 text-sm text-slate-300">
              <input
                v-model="form.sshAuthMethod"
                type="radio"
                value="cloudinit"
                class="text-primary-500"
              />
              <span>Cloud-init (recommended) – use ephemeral SSH keys via cloud-init.</span>
            </label>
            <label class="inline-flex items-center gap-2 text-sm text-slate-300">
              <input
                v-model="form.sshAuthMethod"
                type="radio"
                value="password_reset"
                class="text-primary-500"
              />
              <span>Password reset (legacy) – worker resets a local account password.</span>
            </label>
            <label class="inline-flex items-center gap-2 text-sm text-slate-300">
              <input
                v-model="form.sshAuthMethod"
                type="radio"
                value="static_key"
                class="text-primary-500"
              />
              <span>Static key (advanced) – image contains a pre-baked SSH key.</span>
            </label>
          </div>
          <p class="mt-1 text-xs text-slate-400">
            Use <span class="font-semibold text-slate-200">cloud-init</span> for modern templates with
            ephemeral SSH keys. Other options exist for legacy or special-case images.
          </p>
        </div>

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

        <div class="mt-4 flex items-center gap-3">
          <BaseButton type="button" variant="secondary" @click="testConnection" :disabled="testingConnection">
            {{ testingConnection ? 'Testing...' : 'Test SSH Connection (planned)' }}
          </BaseButton>
          <p class="text-xs text-slate-500">
            This will eventually perform a quick SSH connectivity check against the template. For
            now it is a placeholder and does not perform any backend calls.
          </p>
        </div>

        <div v-if="testResult" class="mt-3 text-xs" :class="testResult.success ? 'text-emerald-400' : 'text-amber-400'">
          {{ testResult.message }}
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
              <span class="text-slate-300">
                {{ cap }}
                <span v-if="cap === 'ssh'" class="block text-[11px] text-slate-500">
                  Template has an SSH server installed and reachable.
                </span>
                <span v-else-if="cap === 'web_server'" class="block text-[11px] text-slate-500">
                  Web server (e.g. Apache/Nginx) installed – required for web exploit scripts.
                </span>
                <span v-else-if="cap === 'database'" class="block text-[11px] text-slate-500">
                  Database service available (e.g. MySQL/PostgreSQL).
                </span>
                <span v-else-if="cap === 'docker'" class="block text-[11px] text-slate-500">
                  Docker or container runtime available on the template.
                </span>
                <span v-else-if="cap === 'systemd'" class="block text-[11px] text-slate-500">
                  Systemd-based init system; services manageable via systemctl.
                </span>
              </span>
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
