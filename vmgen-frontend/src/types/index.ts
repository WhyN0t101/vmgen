export interface Template {
  id: string
  name: string
  os_family: 'ubuntu' | 'debian' | 'centos' | 'windows'
  version: string
  arch: 'x86_64' | 'arm64'
  capabilities: string[]
  description?: string
}

export interface Vulnerability {
  id: string
  name: string
  category: 'web' | 'privesc' | 'auth' | 'service' | 'network'
  difficulty: 'easy' | 'medium' | 'hard'
  requires_capabilities: string[]
  description?: string
}

export interface BuildSpec {
  template_id: string
  vulnerability_ids: string[]
  parameters: {
    difficulty: 'easy' | 'medium' | 'hard'
    telemetry_enabled: boolean
    include_decoys?: boolean
  }
}

export interface Build {
  request_id: string
  user_id: string
  spec: BuildSpec
  state: 'queued' | 'building' | 'exporting' | 'ready' | 'failed' | 'archived'
  progress?: number
  message?: string
  created_at: string
  updated_at: string
  completed_at?: string
  error_message?: string
  ova_url?: string
  provenance_url?: string
  flag_validated: boolean
}

export interface Provenance {
  request_id: string
  template: { id: string; version: string; sha256: string }
  vulnerabilities: Array<{ id: string; version: string }>
  parameters: BuildSpec['parameters']
  seed: number
  build_timestamp: string
  ova_sha256: string
}

export interface User {
  id: string
  email: string
  role: 'user' | 'admin'
  created_at: string
}



