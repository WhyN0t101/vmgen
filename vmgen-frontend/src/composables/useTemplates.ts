import { useQuery } from '@tanstack/vue-query'
import { api } from '@/api/endpoints'

export function useTemplates() {
  return useQuery({
    queryKey: ['templates'],
    queryFn: api.getTemplates,
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
}



