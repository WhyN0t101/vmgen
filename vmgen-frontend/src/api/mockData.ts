import type { Template, Vulnerability, Build } from '@/types'

export const mockTemplates: Template[] = [
  {
    id: 'ubuntu-20.04-v2.1.0',
    name: 'Ubuntu 20.04 LTS',
    os_family: 'ubuntu',
    version: '20.04',
    arch: 'x86_64',
    capabilities: ['supports_ssh', 'has_web_server', 'systemd'],
    description: 'Ubuntu 20.04 LTS with SSH and web server pre-installed',
  },
  {
    id: 'ubuntu-22.04-v1.0.3',
    name: 'Ubuntu 22.04 LTS',
    os_family: 'ubuntu',
    version: '22.04',
    arch: 'x86_64',
    capabilities: ['supports_ssh', 'docker_installed'],
    description: 'Ubuntu 22.04 LTS with Docker support',
  },
  {
    id: 'debian-11-v1.2.0',
    name: 'Debian 11',
    os_family: 'debian',
    version: '11',
    arch: 'x86_64',
    capabilities: ['supports_ssh', 'minimal'],
    description: 'Minimal Debian 11 installation',
  },
]

export const mockVulnerabilities: Vulnerability[] = [
  {
    id: 'sql-injection-v1.0',
    name: 'SQL Injection (Basic)',
    category: 'web',
    difficulty: 'easy',
    requires_capabilities: ['has_web_server'],
    description: 'Vulnerable PHP application with SQL injection',
  },
  {
    id: 'command-injection-v1.0',
    name: 'Command Injection',
    category: 'web',
    difficulty: 'medium',
    requires_capabilities: ['has_web_server'],
    description: 'Web application vulnerable to command injection',
  },
  {
    id: 'xss-stored-v1.0',
    name: 'XSS (Stored)',
    category: 'web',
    difficulty: 'easy',
    requires_capabilities: ['has_web_server'],
    description: 'Stored cross-site scripting vulnerability',
  },
  {
    id: 'sudo-misconfig-v1.0',
    name: 'Sudo Misconfiguration',
    category: 'privesc',
    difficulty: 'medium',
    requires_capabilities: ['supports_ssh'],
    description: 'Misconfigured sudo rules allowing privilege escalation',
  },
  {
    id: 'suid-binary-v1.0',
    name: 'SUID Binary Exploit',
    category: 'privesc',
    difficulty: 'hard',
    requires_capabilities: ['supports_ssh'],
    description: 'SUID binary with exploitable vulnerability',
  },
  {
    id: 'default-creds-v1.0',
    name: 'Default Credentials',
    category: 'auth',
    difficulty: 'easy',
    requires_capabilities: ['supports_ssh'],
    description: 'Weak default credentials for SSH access',
  },
  {
    id: 'weak-ssh-key-v1.0',
    name: 'Weak SSH Key',
    category: 'auth',
    difficulty: 'medium',
    requires_capabilities: ['supports_ssh'],
    description: 'Weak SSH private key for authentication',
  },
]

export const mockBuild: Build = {
  request_id: 'req-a3f9c2b7',
  user_id: 'user-123',
  spec: {
    template_id: 'ubuntu-20.04-v2.1.0',
    vulnerability_ids: ['sql-injection-v1.0', 'sudo-misconfig-v1.0'],
    parameters: { difficulty: 'medium', telemetry_enabled: false },
  },
  state: 'building',
  progress: 65,
  message: 'Injecting vulnerabilities...',
  created_at: new Date(Date.now() - 120000).toISOString(),
  updated_at: new Date().toISOString(),
  flag_validated: false,
}



