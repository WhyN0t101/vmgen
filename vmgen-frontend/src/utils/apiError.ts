/**
 * Extracts a human-readable error message from an API error response.
 * Handles both string[] and {field, message}[] error arrays,
 * then falls back to a top-level message field, then the provided default.
 */
export function extractApiError(error: any, fallback: string): string {
  const errors: unknown[] | undefined = error?.response?.data?.errors
  if (Array.isArray(errors) && errors.length > 0) {
    return errors
      .map((e) => (typeof e === 'string' ? e : (e as any).message ?? String(e)))
      .join('\n')
  }
  return error?.response?.data?.message ?? fallback
}
