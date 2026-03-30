/** Typed error thrown by apiRequest and auth.login — shape expected by extractApiError. */
export interface ApiError extends Error {
  response: { status: number; data: Record<string, unknown> }
}

export type OsFamily = 'ubuntu' | 'debian' | 'kali-linux' | 'fedora' | 'centos' | 'windows' | 'linux'

// New Template Management interfaces (matching backend DTOs)
export type TemplateStatus = 'draft' | 'validating' | 'active' | 'failed' | 'deprecated'
export type Architecture = 'x64' | 'arm64'

export interface TemplateResponse {
  id: string
  identifier: string
  version: string
  osFamily: OsFamily
  osVersion: string
  architecture: Architecture
  description?: string
  proxmoxVmid: number
  proxmoxNode: string
  diskStorage: string
  diskSizeGb: number
  minRamMb: number
  minCpuCores: number
  expectedSshUser: string
  expectedSshPort: number
  networkBridge?: string
  sshAuthMethod: 'cloudinit' | 'password_reset' | 'static_key'
  supportsCloudInit: boolean
  sha256Hash?: string
  status: TemplateStatus
  capabilities: string[]
  validationLogs?: string | Record<string, any>  // JSON string from backend, parsed in frontend
  validationNotes?: string
  lastValidatedAt?: string
  validatedBy?: string
  validatedAt?: string
  createdBy: string
  createdAt: string
  updatedAt: string
  deprecatedAt?: string
}

export interface TemplateListResponse {
  id: string
  identifier: string
  version: string
  osFamily: OsFamily
  description?: string
  status: TemplateStatus
  createdAt: string
}

export interface RegisterTemplateRequest {
  identifier: string
  version: string
  osFamily: OsFamily
  osVersion: string
  architecture: Architecture
  description?: string
  proxmoxVmid: number
  proxmoxNode: string
  diskStorage: string
  diskSizeGb: number
  minRamMb: number
  minCpuCores: number
  expectedSshUser?: string
  expectedSshPort?: number
  networkBridge?: string
  sshAuthMethod: 'cloudinit' | 'password_reset' | 'static_key'
  sshPassword?: string
  sshPrivateKey?: string
  capabilities: string[]
}

// Vulnerability categories
export interface CategoryList {
  id: string
  identifier: string
  name: string
  color?: string
  icon?: string
  owaspMapping?: string
  displayOrder: number
  scriptCount: number
  isActive: boolean
}

export interface ScriptSummary {
  id: string
  identifier: string
  version: string
  name: string
  difficultyLevel: string
  isActive: boolean
}

export interface ScriptListItem extends ScriptSummary {
  description?: string
  categoryId?: string
  categoryName?: string
  categoryColor?: string
  owaspMapping?: string
  createdAt: string
  /** IDs of scripts this script conflicts with (populated by the compatible-scripts endpoint). */
  conflictsWithIds?: string[]
  /** IDs of scripts this script requires as dependencies. */
  requiresIds?: string[]
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface ScriptDetail extends ScriptListItem {
  description?: string
  scriptContent: string
  flagLocation: string
  flagOwnership?: string
  flagPermissions?: string
  createdByUsername?: string
  deprecatedAt?: string
  requiredOsFamilies: string[]
  requiredCapabilities: string[]
  minRamMb?: number | null
  minDiskGb?: number | null
}

// Script compatibility & parameters

export interface ScriptCompatibilityInfo {
  scriptId: string
  conflictsWith: ScriptSummary[]
  requires: ScriptSummary[]
}

export type ParameterType = 'STRING' | 'INTEGER' | 'CHOICE' | 'BOOLEAN' | 'PATH'

export type ValueSource =
  | 'USER_INPUT'
  | 'AUTO_RANDOM'
  | 'AUTO_CHOICE'
  | 'USER_INPUT_OR_RANDOM'

export interface ValidationRules {
  pattern?: string
  min?: number
  max?: number
  minLength?: number
  maxLength?: number
}

export interface RandomConfig {
  length?: number
  charset?: 'alphanumeric' | 'alphanumeric_special' | 'hex' | 'numeric'
  min?: number
  max?: number
}

export interface ParameterChoice {
  id: string
  value: string
  label: string
  description?: string
  displayOrder: number
}

export interface ScriptParameter {
  id: string
  parameterName: string
  displayName: string
  description?: string
  parameterType: ParameterType
  valueSource: ValueSource
  defaultValue?: string
  isRequired: boolean
  validationRules?: ValidationRules
  randomConfig?: RandomConfig
  userCanOverride: boolean
  displayOrder: number
  choices: ParameterChoice[]
}

export interface CreateParameterChoice {
  value: string
  label: string
  description?: string
  displayOrder?: number
}

export interface CreateScriptParameterRequest {
  parameterName: string
  displayName: string
  description?: string
  parameterType: ParameterType
  valueSource: ValueSource
  defaultValue?: string
  isRequired?: boolean
  validationRules?: ValidationRules
  randomConfig?: RandomConfig
  userCanOverride?: boolean
  displayOrder?: number
  choices?: CreateParameterChoice[]
}

export interface UpdateScriptParameterRequest {
  displayName: string
  description?: string
  parameterType: ParameterType
  valueSource: ValueSource
  defaultValue?: string
  isRequired?: boolean
  validationRules?: ValidationRules
  randomConfig?: RandomConfig
  userCanOverride?: boolean
  displayOrder?: number
  choices?: CreateParameterChoice[]
}

export interface CreateScriptRequest {
  identifier: string
  version: string
  name: string
  description?: string
  categoryId: string
  difficultyLevel: string
  scriptContent: string
  flagLocation: string
  flagOwnership?: string
  flagPermissions?: string
  requiredOsFamilies?: string[]
  requiredCapabilities?: string[]
  minRamMb?: number | null
  minDiskGb?: number | null
  conflictsWithScriptIds?: string[]
  requiresScriptIds?: string[]
  parameters?: CreateScriptParameterRequest[]
}

export interface UpdateScriptRequest {
  name: string
  description?: string
  categoryId: string
  difficultyLevel: string
  scriptContent: string
  flagLocation: string
  flagOwnership?: string
  flagPermissions?: string
  requiredOsFamilies?: string[]
  requiredCapabilities?: string[]
  minRamMb?: number | null
  minDiskGb?: number | null
}

export interface CategoryDetail {
  id: string
  identifier: string
  name: string
  description?: string
  color?: string
  icon?: string
  owaspMapping?: string
  severityRange?: string
  isActive: boolean
  displayOrder: number
  createdAt: string
  createdByUsername?: string
  deprecatedAt?: string
  scriptCount: number
  scripts: ScriptSummary[]
}

export interface CreateCategoryRequest {
  identifier: string
  name: string
  description?: string
  color?: string
  icon?: string
  owaspMapping?: string
  severityRange?: string
  displayOrder?: number
}

export interface UpdateCategoryRequest {
  identifier: string
  name: string
  description?: string
  color?: string
  icon?: string
  owaspMapping?: string
  severityRange?: string
  displayOrder?: number
}

export interface CategoryReorderRequest {
  categoryId: string
  newOrder: number
}

export interface StatusResponse {
  status: string
}

export interface UpdateTemplateMetadataRequest {
  description?: string
  expectedSshUser?: string
  expectedSshPort?: number
  networkBridge?: string
  minRamMb?: number
  minCpuCores?: number
}

export interface ProxmoxVmSummary {
  vmid: number
  name: string
  status: string
  cpus: number
  maxmem: number
  isTemplate?: boolean  // true if this is a Proxmox template
}

export interface Vulnerability {
  id: string
  name: string
  category: string
  difficulty: string
  requires_capabilities: string[]
  description?: string
}

export interface BuildSpec {
  templateId: string
  vulnerabilityIds: string[]
  difficulty: 'easy' | 'medium' | 'hard'
  telemetryEnabled: boolean
  /** scriptId → paramName → value for USER_INPUT parameters. */
  scriptParameters?: Record<string, Record<string, string>>
}

export interface BuildScript {
  scriptId: string
  scriptName: string
  executionOrder: number
}

export interface Build {
  id: string
  state: 'queued' | 'building' | 'exporting' | 'ready' | 'failed' | 'archived'
  templateId: string
  templateName: string
  scripts: BuildScript[]
  difficulty?: string
  telemetryEnabled: boolean
  currentWorkerStage?: string
  submittedAt: string
  completedAt?: string
  failedAt?: string
  failureReason?: string
  ovaFilename?: string
  ovaSha256?: string
  ovaSizeBytes?: number
}

export interface ProvenanceResponse {
  buildId: string
  templateIdentifier: string
  templateVersion: string
  templateSha256: string
  buildSeed: string
  difficultyLevel?: string
  telemetryEnabled: boolean
  ovaSha256: string
  flagDerivationTimestamp: string
  finalisedAt: string
}

export interface User {
  id: string
  email: string
  role: 'user' | 'admin'
  created_at: string
}

export interface ActiveSession {
  sessionId: string
  userId: string
  username: string
  role: 'user' | 'admin'
  createdAt: string
  lastActivityAt: string
  expiresAt: string
}

export interface RoleUpdateRequest {
  role: 'user' | 'admin'
}

export interface AuthResponse {
  token: string
  role: string
  expiresAt: string
}

export interface AdminBuildSummary {
  id: string
  state: 'queued' | 'building' | 'exporting' | 'ready' | 'failed' | 'archived'
  userId: string
  username: string
  templateId: string
  templateName: string
  difficulty?: string
  telemetryEnabled: boolean
  currentWorkerStage?: string
  failureReason?: string
  ovaFilename?: string
  ovaSha256?: string
  ovaSizeBytes?: number
  submittedAt: string
  completedAt?: string
  failedAt?: string
}

export interface TelemetryUploadResponse {
  id: string
  buildId: string
  fileSha256: string
  fileSizeBytes: number
  uploadedAt: string
  rawJson: string
}

export interface AdminBuildCredentials {
  buildId: string
  sshUsername: string
  vmLoginPassword: string
}

export interface WorkerStatus {
  workerId: string
  activeJobIds: string[]
  cpuPercent: number | null
  memoryPercent: number | null
  lastSeenAt: string
}

export interface AdminMetrics {
  queueDepth: number
  avgWaitMinutes: number
  completionRate24h: number
  workers: WorkerStatus[]
}



