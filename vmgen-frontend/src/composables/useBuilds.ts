import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { api } from '@/api/endpoints'
import type { BuildSpec } from '@/types'

export function useBuilds() {
  return useQuery({
    queryKey: ['builds'],
    queryFn: api.getUserBuilds,
  })
}

export function useCreateBuild() {
  const router = useRouter()
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: (spec: BuildSpec) => api.createBuild(spec),
    onSuccess: (data) => {
      // Invalidate builds list
      queryClient.invalidateQueries({ queryKey: ['builds'] })
      // Navigate to status page
      router.push({ name: 'build-status', params: { requestId: data.request_id } })
    },
  })
}

export function useSubmitFlag() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: ({ requestId, flag }: { requestId: string; flag: string }) =>
      api.submitFlag(requestId, flag),
    onSuccess: (_, variables) => {
      // Invalidate build status and builds list
      queryClient.invalidateQueries({ queryKey: ['build', variables.requestId] })
      queryClient.invalidateQueries({ queryKey: ['builds'] })
    },
  })
}



