import type { ParameterType, ValidationRules, CreateScriptParameterRequest } from '@/types'

/**
 * Returns a blank CreateScriptParameterRequest with sensible defaults.
 * Pass displayOrder to pre-fill the position in the list.
 */
export function defaultParameterForm(displayOrder = 0): CreateScriptParameterRequest {
  return {
    parameterName: '',
    displayName: '',
    description: '',
    parameterType: 'STRING',
    valueSource: 'USER_INPUT',
    defaultValue: '',
    isRequired: true,
    validationRules: {},
    randomConfig: {},
    userCanOverride: false,
    displayOrder,
    choices: [],
  }
}

/**
 * Strips empty/invalid fields from a ValidationRules object based on the parameter type.
 * Returns undefined if no meaningful rules remain.
 */
export function cleanRules(
  type: ParameterType,
  rules: ValidationRules | undefined,
): ValidationRules | undefined {
  if (!rules) return undefined
  const out: ValidationRules = {}
  if (type === 'INTEGER') {
    if (rules.min != null && rules.min !== ('' as any)) out.min = Number(rules.min)
    if (rules.max != null && rules.max !== ('' as any)) out.max = Number(rules.max)
  } else if (type === 'STRING' || type === 'PATH') {
    if (rules.minLength != null && rules.minLength !== ('' as any)) out.minLength = Number(rules.minLength)
    if (rules.maxLength != null && rules.maxLength !== ('' as any)) out.maxLength = Number(rules.maxLength)
    if (rules.pattern?.trim()) {
      const trimmed = rules.pattern.trim()
      try {
        new RegExp(trimmed) // validate it compiles before persisting
        out.pattern = trimmed
      } catch {
        // invalid regex — skip silently; backend will reject on parameter submission
      }
    }
  }
  return Object.keys(out).length > 0 ? out : undefined
}
