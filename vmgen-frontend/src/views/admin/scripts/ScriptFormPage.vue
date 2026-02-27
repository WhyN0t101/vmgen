<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { useCategoryList } from '@/composables/useCategories'
import {
  useCreateScript,
  useScriptDetail,
  useUpdateScript,
  useScriptCompatibility,
  useScriptList,
} from '@/composables/useScripts'
import ScriptCompatibilityEditor from '@/components/admin/ScriptCompatibilityEditor.vue'
import ScriptParameterEditor from '@/components/admin/ScriptParameterEditor.vue'
import ScriptCompatibilityDraftEditor from '@/components/admin/ScriptCompatibilityDraftEditor.vue'
import ScriptParameterDraftEditor from '@/components/admin/ScriptParameterDraftEditor.vue'
import type { CreateScriptRequest, CreateScriptParameterRequest, UpdateScriptRequest } from '@/types'

const router = useRouter()
const route = useRoute()

const isEditing = computed(() => !!route.params.id)
const scriptId = computed(() => (route.params.id as string) || '')

const { data: categoriesData } = useCategoryList(true)
const categories = computed(() => categoriesData.value ?? [])

const { data: existingScript } = useScriptDetail(scriptId)
const { data: compatibility, refetch: refetchCompatibility } = useScriptCompatibility(scriptId)

// For compatibility editor we need a flat list of scripts
const activeOnly = ref(true)
const categoryFilter = ref<string | ''>('')
const difficultyFilter = ref<string | ''>('')
const scriptListQuery = useScriptList({
  categoryId: categoryFilter,
  difficulty: difficultyFilter,
  activeOnly,
})

const createMutation = useCreateScript()
const updateMutation = useUpdateScript()

const isSubmitting = computed(
  () => createMutation.isPending.value || updateMutation.isPending.value,
)

type ScriptFormState = CreateScriptRequest & { id?: string }

const form = ref<ScriptFormState>({
  identifier: '',
  version: 'v1.0.0',
  name: '',
  description: '',
  categoryId: '',
  difficultyLevel: 'MEDIUM',
  scriptContent: '',
  flagLocation: '/root/flag.txt',
  flagOwnership: '',
  flagPermissions: '0644',
  requiredOsFamilies: ['linux'],
  requiredCapabilities: ['ssh'],
  minRamMb: null,
  minDiskGb: null,
  conflictsWithScriptIds: [],
  requiresScriptIds: [],
  parameters: [],
})

onMounted(() => {
  if (isEditing.value && existingScript.value) {
    const s = existingScript.value
    form.value = {
      id: s.id,
      identifier: s.identifier,
      version: s.version,
      name: s.name,
      description: s.description,
      categoryId: s.categoryId || '',
      difficultyLevel: s.difficultyLevel,
      scriptContent: s.scriptContent,
      flagLocation: s.flagLocation,
      flagOwnership: s.flagOwnership,
      flagPermissions: s.flagPermissions || '0644',
      requiredOsFamilies: s.requiredOsFamilies || ['linux'],
      requiredCapabilities: s.requiredCapabilities || ['ssh'],
      minRamMb: s.minRamMb ?? null,
      minDiskGb: s.minDiskGb ?? null,
    }
  }
})

function handleCancel() {
  router.back()
}

async function handleSubmit() {
  if (!form.value.categoryId) {
    alert('Please select a vulnerability category.')
    return
  }

  if (isEditing.value && form.value.id) {
    const payload: UpdateScriptRequest = {
      name: form.value.name,
      description: form.value.description,
      categoryId: form.value.categoryId,
      difficultyLevel: form.value.difficultyLevel,
      scriptContent: form.value.scriptContent,
      flagLocation: form.value.flagLocation,
      flagOwnership: form.value.flagOwnership,
      flagPermissions: form.value.flagPermissions,
      requiredOsFamilies: form.value.requiredOsFamilies,
      requiredCapabilities: form.value.requiredCapabilities,
      minRamMb: form.value.minRamMb,
      minDiskGb: form.value.minDiskGb,
    }
    await updateMutation.mutateAsync({ id: form.value.id, data: payload })
  } else {
    const created = await createMutation.mutateAsync(form.value as CreateScriptRequest)
    // After creating a script, go straight to the edit view so
    // compatibility and parameters can be configured immediately.
    if (created && created.id) {
      router.push({ name: 'admin-script-edit', params: { id: created.id } })
      return
    }
  }

  router.push({ name: 'admin-scripts' })
}
</script>

<template>
  <div class="container mx-auto px-6 py-8 max-w-4xl">
    <div class="mb-6">
      <h1 class="text-3xl font-bold text-slate-100">
        {{ isEditing ? 'Edit Vulnerability Script' : 'Create Vulnerability Script' }}
      </h1>
      <p class="text-sm text-slate-400">
        Define how this script injects a vulnerability into a template and how the flag is placed.
      </p>
    </div>

    <BaseCard>
      <form class="space-y-8" @submit.prevent="handleSubmit">
        <!-- Basic Information -->
        <section class="space-y-4 border-b border-slate-800 pb-6">
          <h2 class="text-lg font-semibold text-slate-100">Basic information</h2>

          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Identifier</label>
              <input
                v-model="form.identifier"
                type="text"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                pattern="[a-z0-9-]+"
                :disabled="isEditing"
                required
              />
              <p class="mt-1 text-[11px] text-slate-500">
                Lowercase letters, numbers, and hyphens only. Cannot be changed after creation.
              </p>
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Version</label>
              <input
                v-model="form.version"
                type="text"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                pattern="v\d+\.\d+\.\d+"
                :disabled="isEditing"
                required
              />
              <p class="mt-1 text-[11px] text-slate-500">Format: vX.Y.Z</p>
            </div>
          </div>

          <div>
            <label class="mb-1 block text-sm font-medium text-slate-300">Name</label>
            <input
              v-model="form.name"
              type="text"
              class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
              placeholder="SQL injection in PHP login form"
              required
            />
            <p class="mt-1 text-[11px] text-slate-500">
              This is the human-readable title shown in the UI. The identifier above is a stable slug
              (e.g. <span class="font-mono text-slate-400">sqli-login-v1</span>) and must be lowercase
              letters, numbers, and hyphens.
            </p>
          </div>

          <div>
            <label class="mb-1 block text-sm font-medium text-slate-300">Description</label>
            <textarea
              v-model="form.description"
              rows="3"
              class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
              placeholder="Explain what this script does, how the vulnerability is exposed, and what the learner should practice."
            />
          </div>
        </section>

        <!-- Classification -->
        <section class="space-y-4 border-b border-slate-800 pb-6">
          <h2 class="text-lg font-semibold text-slate-100">Classification</h2>

          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Vulnerability category</label>
              <select
                v-model="form.categoryId"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                required
              >
                <option value="">Select category…</option>
                <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                  {{ cat.name }}{{ cat.owaspMapping ? ` (${cat.owaspMapping})` : '' }}
                </option>
              </select>
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Difficulty</label>
              <div class="flex gap-4 text-sm text-slate-200">
                <label class="flex items-center gap-2">
                  <input v-model="form.difficultyLevel" type="radio" value="EASY" />
                  Easy
                </label>
                <label class="flex items-center gap-2">
                  <input v-model="form.difficultyLevel" type="radio" value="MEDIUM" />
                  Medium
                </label>
                <label class="flex items-center gap-2">
                  <input v-model="form.difficultyLevel" type="radio" value="HARD" />
                  Hard
                </label>
              </div>
            </div>
          </div>
        </section>

        <!-- Requirements -->
        <section class="space-y-4 border-b border-slate-800 pb-6">
          <h2 class="text-lg font-semibold text-slate-100">Template requirements</h2>
          <p class="text-xs text-slate-400">
            These fields describe which templates this script can be safely applied to. They will be used
            by the compatibility system.
          </p>

          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Required OS families</label>
              <div class="flex flex-wrap gap-3 text-sm text-slate-200">
                <label class="flex items-center gap-2">
                  <input
                    v-model="form.requiredOsFamilies"
                    type="checkbox"
                    value="linux"
                  />
                  Linux
                </label>
                <label class="flex items-center gap-2">
                  <input
                    v-model="form.requiredOsFamilies"
                    type="checkbox"
                    value="windows"
                  />
                  Windows
                </label>
              </div>
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Required capabilities</label>
              <div class="flex flex-wrap gap-3 text-sm text-slate-200">
                <label class="flex items-center gap-2">
                  <input
                    v-model="form.requiredCapabilities"
                    type="checkbox"
                    value="ssh"
                  />
                  SSH
                </label>
                <label class="flex items-center gap-2">
                  <input
                    v-model="form.requiredCapabilities"
                    type="checkbox"
                    value="web_server"
                  />
                  Web server
                </label>
                <label class="flex items-center gap-2">
                  <input
                    v-model="form.requiredCapabilities"
                    type="checkbox"
                    value="database"
                  />
                  Database
                </label>
                <label class="flex items-center gap-2">
                  <input
                    v-model="form.requiredCapabilities"
                    type="checkbox"
                    value="docker"
                  />
                  Docker
                </label>
                <label class="flex items-center gap-2">
                  <input
                    v-model="form.requiredCapabilities"
                    type="checkbox"
                    value="systemd"
                  />
                  Systemd
                </label>
              </div>
            </div>
          </div>

          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Minimum RAM (MB)</label>
              <input
                v-model.number="form.minRamMb"
                type="number"
                min="512"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                placeholder="1024"
              />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Minimum disk size (GB)</label>
              <input
                v-model.number="form.minDiskGb"
                type="number"
                min="1"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                placeholder="2"
              />
            </div>
          </div>
        </section>

        <!-- Script content -->
        <section class="space-y-4 border-b border-slate-800 pb-6">
          <h2 class="text-lg font-semibold text-slate-100">Script content</h2>
          <div>
            <label class="mb-1 block text-sm font-medium text-slate-300">Bash script</label>
            <textarea
              v-model="form.scriptContent"
              rows="16"
              class="w-full rounded-md border border-slate-700 bg-slate-950 px-3 py-2 text-sm font-mono text-slate-100"
              placeholder="#!/bin/bash&#10;# Vulnerability injection script..."
              required
            />
            <p class="mt-1 text-[11px] text-slate-500">
              This script will be executed as part of the build pipeline inside the target VM.
            </p>
          </div>
        </section>

        <!-- Flag configuration -->
        <section class="space-y-4">
          <h2 class="text-lg font-semibold text-slate-100">Flag configuration</h2>
          <div>
            <label class="mb-1 block text-sm font-medium text-slate-300">Flag location</label>
            <input
              v-model="form.flagLocation"
              type="text"
              class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
              required
            />
            <p class="mt-1 text-[11px] text-slate-500">
              Absolute path where the derived flag will be written (e.g. /opt/vmgen/flags/sql1.txt).
            </p>
          </div>
          <div class="grid gap-4 md:grid-cols-2">
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">File ownership</label>
              <input
                v-model="form.flagOwnership"
                type="text"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                placeholder="www-data:www-data"
              />
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">File permissions</label>
              <input
                v-model="form.flagPermissions"
                type="text"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                placeholder="0644"
              />
              <p class="mt-1 text-[11px] text-slate-500">Octal format (e.g. 0644).</p>
            </div>
          </div>
        </section>

        <!-- Compatibility & parameters -->
        <section class="space-y-4 border-t border-slate-800 pt-6">
          <template v-if="isEditing && existingScript">
            <ScriptCompatibilityEditor
              :script="existingScript"
              :all-scripts="scriptListQuery.data.value ?? []"
              :compatibility="compatibility || null"
              @refresh="refetchCompatibility()"
            />
            <ScriptParameterEditor :script="existingScript" />
          </template>

          <template v-else>
            <ScriptCompatibilityDraftEditor
              :script-name="form.name || 'this script'"
              :all-scripts="scriptListQuery.data.value ?? []"
              :conflicts-with-script-ids="form.conflictsWithScriptIds || []"
              :requires-script-ids="form.requiresScriptIds || []"
              @update:conflictsWithScriptIds="(v) => (form.conflictsWithScriptIds = v)"
              @update:requiresScriptIds="(v) => (form.requiresScriptIds = v)"
            />
            <ScriptParameterDraftEditor
              :parameters="(form.parameters as CreateScriptParameterRequest[]) || []"
              @update:parameters="(v) => (form.parameters = v)"
            />
          </template>
        </section>

        <div class="flex justify-end gap-3 pt-4">
          <BaseButton type="button" variant="ghost" @click="handleCancel">
            Cancel
          </BaseButton>
          <BaseButton type="submit" :loading="isSubmitting">
            {{ isEditing ? 'Update Script' : 'Create Script' }}
          </BaseButton>
        </div>
      </form>
    </BaseCard>
  </div>
</template>

