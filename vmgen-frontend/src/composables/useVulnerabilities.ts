import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { api } from '@/api/endpoints'

/**
 * Fetch vulnerability scripts for a given template identifier so the
 * Generate page can present compatible vulnerability choices.
 */
export function useVulnerabilities(templateId: MaybeRefOrGetter<string | undefined>) {
  const templateIdValue = computed(() => toValue(templateId))
  const queryKey = computed(() => ['vulnerabilities', templateIdValue.value])
  
  return useQuery({
    queryKey,
    queryFn: () => api.getVulnerabilities(templateIdValue.value),
    enabled: computed(() => !!templateIdValue.value),
    staleTime: 5 * 60 * 1000,
  })
}

