import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { api } from '@/api/endpoints'
import type { BuildSpec } from '@/types'

/**
 * Fetch the current user's VM build history for dashboard views.
 * Returns paginated results; page defaults to 0, size to 20.
 */
export function useBuilds(page = 0, size = 20) {
  return useQuery({
    queryKey: ['builds', page, size],
    queryFn: () => api.getUserBuilds(page, size),
  })
}

/**
 * Create a new VM build and navigate to its status page,
 * invalidating any cached build lists on success.
 */
export function useCreateBuild() {
  const router = useRouter()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (spec: BuildSpec) => api.createBuild(spec),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['builds'] })
      router.push({ name: 'build-status', params: { requestId: data.id } })
    },
  })
}

/**
 * Submit a captured flag for a given build and refresh the
 * corresponding build status and build list queries.
 */
export function useSubmitFlag() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ requestId, flag }: { requestId: string; flag: string }) =>
      api.submitFlag(requestId, flag),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['build', variables.requestId] })
      queryClient.invalidateQueries({ queryKey: ['builds'] })
    },
  })
}
