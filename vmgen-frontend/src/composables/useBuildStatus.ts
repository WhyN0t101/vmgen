import { useQuery } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { api } from '@/api/endpoints'

export function useBuildStatus(requestId: MaybeRefOrGetter<string>) {
  const requestIdValue = computed(() => toValue(requestId))
  const queryKey = computed(() => ['build', requestIdValue.value])
  
  return useQuery({
    queryKey,
    queryFn: () => api.getBuildStatus(requestIdValue.value),
    refetchInterval: (query) => {
      // Poll every 2 seconds if building, stop if complete/failed
      const data = query.state.data
      if (data?.state === 'building' || data?.state === 'queued' || data?.state === 'exporting') {
        return 2000
      }
      return false
    },
  })
}
