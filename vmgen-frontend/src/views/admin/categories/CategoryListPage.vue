<script setup lang="ts">
import { ref, computed } from 'vue'
import { useCategoryList, useCreateOrUpdateCategory, useDeprecateCategory, useReorderCategories } from '@/composables/useCategories'
import type { CategoryList, CreateCategoryRequest, UpdateCategoryRequest } from '@/types'
import BaseCard from '@/components/ui/BaseCard.vue'
import BaseButton from '@/components/ui/BaseButton.vue'

type CategoryFormState = (CreateCategoryRequest & { id?: string })

const showForm = ref(false)
const editingCategory = ref<CategoryFormState | null>(null)
const activeOnly = ref(true)

const { data: categoriesData, isLoading, refetch } = useCategoryList(activeOnly)
const categories = computed(() => categoriesData.value ?? [])

const saveMutation = useCreateOrUpdateCategory()
const deprecateMutation = useDeprecateCategory()
const reorderMutation = useReorderCategories()

function openCreate() {
  editingCategory.value = {
    identifier: '',
    name: '',
    description: '',
    color: '#E74C3C',
    icon: 'database-slash',
    owaspMapping: '',
    severityRange: '',
    displayOrder: (categories.value[categories.value.length - 1]?.displayOrder ?? 0) + 1,
  }
  showForm.value = true
}

function openEdit(cat: CategoryList) {
  editingCategory.value = {
    id: cat.id,
    identifier: cat.identifier,
    name: cat.name,
    description: '',
    color: cat.color,
    icon: cat.icon,
    owaspMapping: cat.owaspMapping,
    severityRange: '',
    displayOrder: cat.displayOrder,
  }
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editingCategory.value = null
}

async function handleSave() {
  if (!editingCategory.value) return
  await saveMutation.mutateAsync({ id: editingCategory.value.id, data: editingCategory.value })
  closeForm()
  refetch()
}

async function handleDeprecate(id: string) {
  if (!confirm('Deprecate this category? It will no longer be available for new scripts.')) return
  await deprecateMutation.mutateAsync(id)
  refetch()
}

async function moveCategory(cat: CategoryList, direction: 'up' | 'down') {
  // Work on a copy ordered by current displayOrder so that we can compute
  // stable, sequential positions even if existing orders are identical.
  const ordered = [...categories.value].sort((a, b) => a.displayOrder - b.displayOrder)
  const index = ordered.findIndex(c => c.id === cat.id)
  if (index === -1) return

  const targetIndex = direction === 'up' ? index - 1 : index + 1
  if (targetIndex < 0 || targetIndex >= ordered.length) return

  const [moved] = ordered.splice(index, 1)
  ordered.splice(targetIndex, 0, moved)

  // Reassign display_order based on new index to avoid duplicates (0..n-1).
  const payload = ordered.map((c, idx) => ({
    categoryId: c.id,
    newOrder: idx,
  }))

  await reorderMutation.mutateAsync(payload)
  refetch()
}
</script>

<template>
  <div class="max-w-5xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-8 md:py-7 space-y-6">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-[11px] font-mono tracking-[0.25em] uppercase text-slate-500 mb-1">
            Module // Categories
          </p>
          <h1 class="text-2xl md:text-3xl font-semibold text-slate-50">
            Vulnerability Categories
          </h1>
          <p class="text-xs md:text-sm text-slate-400 mt-1">
            Organize vulnerability scripts into editable, OWASP-mapped categories.
          </p>
        </div>
        <BaseButton variant="primary" @click="openCreate">
          New Category
        </BaseButton>
      </div>

      <div v-if="isLoading" class="space-y-3">
        <BaseCard v-for="i in 3" :key="i" class="h-20 animate-pulse bg-slate-900/60 border-slate-800/80" />
      </div>

      <div v-else>
        <div class="flex items-center justify-end gap-3">
          <label class="flex items-center gap-2 text-xs text-slate-400">
            <input
              v-model="activeOnly"
              type="checkbox"
              class="rounded border-slate-600 bg-slate-950/60"
            />
            Active only
          </label>
        </div>

        <div v-if="categories.length > 0" class="space-y-3 mt-3">
        <BaseCard
          v-for="cat in categories"
          :key="cat.id"
          padding="md"
          class="flex items-center justify-between gap-4 border-l-4 hover:border-sky-400/80 hover:bg-slate-900/80 transition-colors duration-150"
          :style="{ borderColor: cat.color || '#64748b' }"
        >
          <div class="flex items-start gap-4">
            <div class="flex flex-col items-center gap-2 text-slate-500">
              <button
                class="hover:text-slate-100 text-xs"
                @click="moveCategory(cat, 'up')"
                title="Move up"
              >
                ▲
              </button>
              <button
                class="hover:text-slate-100 text-xs"
                @click="moveCategory(cat, 'down')"
                title="Move down"
              >
                ▼
              </button>
            </div>
            <div>
              <div class="mb-1 flex items-center gap-2">
                <span
                  class="inline-flex h-5 w-5 items-center justify-center rounded-full"
                  :style="{ backgroundColor: cat.color || '#334155' }"
                />
                <h2 class="text-lg font-semibold text-slate-100">
                  {{ cat.name }}
                </h2>
              </div>
              <p class="text-xs font-mono text-slate-500">{{ cat.identifier }}</p>
              <p
                v-if="cat.owaspMapping"
                class="mt-1 inline-block rounded bg-slate-900 px-2 py-0.5 text-[11px] text-slate-300"
              >
                {{ cat.owaspMapping }}
              </p>
              <p
                v-if="cat.icon"
                class="mt-1 text-[11px] text-slate-500"
              >
                Icon: {{ cat.icon }}
              </p>
            </div>
          </div>

          <div class="flex items-center gap-4">
            <div class="text-right">
              <p class="text-lg font-semibold text-slate-100">
                {{ cat.scriptCount }}
              </p>
              <p class="text-xs text-slate-400">
                script{{ cat.scriptCount === 1 ? '' : 's' }}
              </p>
            </div>
            <div class="flex gap-2">
              <BaseButton variant="secondary" size="sm" @click="openEdit(cat)">
                Edit
              </BaseButton>
              <BaseButton
                variant="ghost"
                size="sm"
                :disabled="!cat.isActive || deprecateMutation.isPending.value"
                @click="handleDeprecate(cat.id)"
              >
                Deprecate
              </BaseButton>
            </div>
          </div>
        </BaseCard>
        </div>

        <div v-else class="py-16 text-center text-slate-400">
          <p v-if="activeOnly">
            No active categories. Disable <span class="font-semibold">Active only</span> to see deprecated categories.
          </p>
          <p v-else>
            No categories defined yet.
          </p>
        </div>
      </div>

      <div
        v-if="showForm && editingCategory"
        class="fixed inset-0 z-40 flex items-center justify-center bg-black/60"
      >
        <div class="w-full max-w-xl rounded-lg bg-slate-950/95 border border-slate-700 shadow-xl">
          <div class="flex items-center justify-between border-b border-slate-800 px-5 py-3">
            <h2 class="text-lg font-semibold text-slate-100">
              {{ editingCategory.id ? 'Edit Category' : 'Create Category' }}
            </h2>
            <button class="text-slate-500 hover:text-slate-200" @click="closeForm">✕</button>
          </div>

          <form class="space-y-4 px-5 py-4" @submit.prevent="handleSave">
            <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label class="mb-1 block text-sm font-medium text-slate-300">Identifier</label>
                <input
                  v-model="editingCategory.identifier"
                  type="text"
                  class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                  pattern="[a-z0-9-]+"
                  :disabled="!!editingCategory.id"
                  required
                />
                <p class="mt-1 text-[11px] text-slate-500">
                  Lowercase letters, numbers, and hyphens. Cannot be changed after creation.
                </p>
              </div>
              <div>
                <label class="mb-1 block text-sm font-medium text-slate-300">Display name</label>
                <input
                  v-model="editingCategory.name"
                  type="text"
                  class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                  required
                />
              </div>
            </div>

            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Description</label>
              <textarea
                v-model="editingCategory.description"
                rows="3"
                class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                placeholder="Short explanation of this vulnerability category and its learning goals."
              />
            </div>

            <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label class="mb-1 block text-sm font-medium text-slate-300">Color</label>
                <div class="flex items-center gap-2">
                  <input
                    v-model="editingCategory.color"
                    type="color"
                    class="h-9 w-9 cursor-pointer rounded border border-slate-700 bg-slate-900"
                  />
                  <input
                    v-model="editingCategory.color"
                    type="text"
                    class="flex-1 rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                    placeholder="#E74C3C"
                    pattern="^#[0-9A-Fa-f]{6}$"
                  />
                </div>
                <p class="mt-1 text-[11px] text-slate-500">Used for badges and visual grouping.</p>
              </div>
              <div>
                <label class="mb-1 block text-sm font-medium text-slate-300">Icon identifier</label>
                <input
                  v-model="editingCategory.icon"
                  type="text"
                  class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                  placeholder="database-slash"
                />
                <p class="mt-1 text-[11px] text-slate-500">
                  Matches the frontend icon set (e.g. Lucide icon name).
                </p>
              </div>
            </div>

            <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label class="mb-1 block text-sm font-medium text-slate-300">OWASP mapping</label>
                <input
                  v-model="editingCategory.owaspMapping"
                  type="text"
                  class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                  placeholder="A03:2021 - Injection"
                />
              </div>
              <div>
                <label class="mb-1 block text-sm font-medium text-slate-300">Severity (optional)</label>
                <select
                  v-model="editingCategory.severityRange"
                  class="w-full rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
                >
                  <option value="">Not specified</option>
                  <option value="low">Low</option>
                  <option value="medium">Medium</option>
                  <option value="high">High</option>
                  <option value="critical">Critical</option>
                </select>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-sm font-medium text-slate-300">Display order</label>
              <input
                v-model.number="editingCategory.displayOrder"
                type="number"
                min="0"
                class="w-32 rounded-md border border-slate-700 bg-slate-900 px-3 py-2 text-sm text-slate-100"
              />
            </div>

            <div class="flex justify-end gap-3 border-t border-slate-800 pt-4">
              <BaseButton type="button" variant="ghost" @click="closeForm">
                Cancel
              </BaseButton>
              <BaseButton type="submit" :loading="saveMutation.isPending.value">
                {{ editingCategory.id ? 'Update Category' : 'Create Category' }}
              </BaseButton>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

