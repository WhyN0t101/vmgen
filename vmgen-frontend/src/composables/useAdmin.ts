import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { api } from '@/api/endpoints'
import type { ActiveSession } from '@/types'

export function useActiveSessions() {
  return useQuery({
    queryKey: ['admin', 'sessions'],
    queryFn: () => api.admin.getActiveSessions(),
    refetchInterval: 10000, // Refresh every 10 seconds
  })
}

export function useRevokeSession() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: (sessionId: string) => api.admin.revokeSession(sessionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'sessions'] })
    },
  })
}

export function useRevokeAllUserSessions() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: (userId: string) => api.admin.revokeAllUserSessions(userId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'sessions'] })
    },
  })
}

export function useUpdateUserRole() {
  const queryClient = useQueryClient()
  
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: 'user' | 'admin' }) =>
      api.admin.updateUserRole(userId, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'sessions'] })
    },
  })
}
