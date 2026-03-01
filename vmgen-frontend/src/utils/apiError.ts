/**
 * Extracts a human-readable error message from an Axios-style error response.
 * Prefers a joined validation errors array, then a single message field,
 * then falls back to the provided default string.
 */
export function extractApiError(error: any, fallback: string): string {
  return (
    error?.response?.data?.errors?.join?.('\n') ??
    error?.response?.data?.message ??
    fallback
  )
}
