<script setup lang="ts">
import { computed, ref } from 'vue'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import type { TemplateResponse, ScriptListItem } from '@/types'
import { api } from '@/api/endpoints'
import { useToast } from '@/composables/useToast'

// Editor for managing which scripts are compatible with a specific template.
// Admins can add or remove scripts from the template's compatibility list.
const props = defineProps<{
  template: TemplateResponse
  allScripts: ScriptListItem[]
  compatibleScripts: ScriptListItem[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const { show } = useToast()

const selectedScript = ref('')

const compatibleIds = computed(() => new Set(props.compatibleScripts.map(s => s.id)))

const uniqueCompatible = computed(() => {
  const seen = new Set<string>()
  return props.compatibleScripts.filter(s => {
    if (seen.has(s.id)) return false
    seen.add(s.id)
    return true
  })
})

// Only show scripts not already linked
const availableScripts = computed(() =>
  props.allScripts.filter(s => !compatibleIds.value.has(s.id)),
)

async function addScript() {
  if (!selectedScript.value) return
  try {
    await api.admin.addTemplateCompatibleScript(props.template.id.toString(), selectedScript.value)
  } catch (err: any) {
    const msg =
      err?.response?.data?.errors?.join?.('\n') ??
      err?.response?.data?.message ??
      'Failed to add script'
    show(msg, 'error')
  } finally {
    selectedScript.value = ''
    emit('refresh')
  }
}

async function removeScript(scriptId: string) {
  try {
    await api.admin.removeTemplateCompatibleScript(props.template.id.toString(), scriptId)
  } catch (err: any) {
    const msg =
      err?.response?.data?.message ?? 'Failed to remove script'
    show(msg, 'error')
  } finally {
    emit('refresh')
  }
}
</script>

<template>
  <BaseCard class="space-y-4">
    <div>
      <h2 class="text-lg font-semibold text-slate-100">Compatible Scripts</h2>
      <p class="text-xs text-slate-400 mt-1">
        Scripts listed here are the only ones users can select when generating a VM from
        <span class="font-semibold">{{ template.identifier }} {{ template.version }}</span>.
        If the list is empty, all active scripts are available.
      </p>
    </div>

    <div class="space-y-2">
      <!-- Current compatible scripts -->
      <div
        v-for="s in uniqueCompatible"
        :key="s.id"
        class="flex items-center justify-between rounded border border-slate-700 bg-slate-900/70 px-3 py-2"
      >
        <div>
          <p class="text-sm text-slate-100">{{ s.name }}</p>
          <p class="text-[11px] font-mono text-slate-500">
            {{ s.identifier }}@{{ s.version }}
            <span v-if="s.categoryName" class="ml-2 text-slate-400">· {{ s.categoryName }}</span>
          </p>
        </div>
        <BaseButton type="button" variant="ghost" size="sm" @click="removeScript(s.id)">
          Remove
        </BaseButton>
      </div>

      <p v-if="uniqueCompatible.length === 0" class="text-sm text-slate-500 italic">
        No scripts linked — all active scripts are shown to users.
      </p>

      <!-- Add script row -->
      <div class="flex items-stretch gap-2 pt-2">
        <select
          v-model="selectedScript"
          class="flex-1 h-10 rounded-md border border-slate-700 bg-slate-900 px-3 text-sm text-slate-100"
        >
          <option value="">Add compatible script…</option>
          <option
            v-for="s in availableScripts"
            :key="s.id"
            :value="s.id"
          >
            {{ s.name }} ({{ s.identifier }}@{{ s.version }})
          </option>
        </select>
        <BaseButton
          type="button"
          size="sm"
          class="h-10"
          :disabled="!selectedScript"
          @click="addScript"
        >
          Add
        </BaseButton>
      </div>
    </div>
  </BaseCard>
</template>
