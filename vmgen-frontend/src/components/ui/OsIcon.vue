<script setup lang="ts">
defineProps<{ osFamily: string; size?: number }>()
</script>

<template>
  <!-- Ubuntu -->
  <svg v-if="osFamily === 'ubuntu'" :width="size ?? 28" :height="size ?? 28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="12" cy="12" r="11" fill="#E95420"/>
    <circle cx="12" cy="5.5" r="2.2" fill="white"/>
    <circle cx="6.1" cy="15.75" r="2.2" fill="white"/>
    <circle cx="17.9" cy="15.75" r="2.2" fill="white"/>
    <path d="M10.1 7.1 L7.4 14.3 M13.9 7.1 L16.6 14.3 M8.2 17 L15.8 17" stroke="white" stroke-width="0.7" opacity="0.45"/>
  </svg>

  <!-- Debian -->
  <svg v-else-if="osFamily === 'debian'" :width="size ?? 28" :height="size ?? 28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="12" cy="12" r="11" fill="#A80030"/>
    <path d="M16.5 8.5C15.2 6.8 13.2 5.8 11 6C7.7 6.3 5.2 9.2 5.5 12.5C5.8 15.8 8.7 18.3 12 18C13.8 17.8 15.3 16.9 16.2 15.6" stroke="white" stroke-width="1.8" stroke-linecap="round" fill="none"/>
    <path d="M12 9.5C11 9.5 10 10.5 10 12s1 2.5 2 2.5" stroke="white" stroke-width="1.4" stroke-linecap="round" fill="none"/>
  </svg>

  <!-- Kali Linux -->
  <svg v-else-if="osFamily === 'kali-linux'" :width="size ?? 28" :height="size ?? 28" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
    <rect width="24" height="24" rx="4" fill="#1A1A2E"/>
    <path d="M12 2.5L7.5 13H10.5L9.5 21.5L16.5 11H13.5L12 2.5Z" fill="#367BF0"/>
  </svg>

  <!-- Fedora -->
  <svg v-else-if="osFamily === 'fedora'" :width="size ?? 28" :height="size ?? 28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="12" cy="12" r="11" fill="#294172"/>
    <path d="M12 6a6 6 0 0 1 6 6v5h-2.5v-5a3.5 3.5 0 0 0-7 0v5H6v-5a6 6 0 0 1 6-6z" fill="#5FAAE0"/>
    <rect x="6" y="10.5" width="5.5" height="2" rx="1" fill="#5FAAE0"/>
  </svg>

  <!-- CentOS / Rocky -->
  <svg v-else-if="osFamily === 'centos'" :width="size ?? 28" :height="size ?? 28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="12" cy="12" r="11" fill="#262577"/>
    <path d="M12 3L8.5 8.5H3L8.5 12L3 15.5H8.5L12 21L15.5 15.5H21L15.5 12L21 8.5H15.5L12 3Z" stroke="#9B59B6" stroke-width="1.3" fill="none"/>
    <path d="M12 7.5L9.5 12L12 16.5L14.5 12L12 7.5Z" fill="#9B59B6"/>
  </svg>

  <!-- Windows -->
  <svg v-else-if="osFamily === 'windows'" :width="size ?? 28" :height="size ?? 28" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
    <rect x="1" y="1" width="10" height="10" rx="1" fill="#F25022"/>
    <rect x="13" y="1" width="10" height="10" rx="1" fill="#7FBA00"/>
    <rect x="1" y="13" width="10" height="10" rx="1" fill="#00A4EF"/>
    <rect x="13" y="13" width="10" height="10" rx="1" fill="#FFB900"/>
  </svg>

  <!-- Generic Linux / fallback -->
  <svg v-else :width="size ?? 28" :height="size ?? 28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="12" cy="12" r="11" fill="#2C2C2C"/>
    <rect x="5.5" y="7" width="13" height="9" rx="1.5" stroke="#22C55E" stroke-width="1.5"/>
    <path d="M8 10.5l2 1.5-2 1.5M12.5 14h3.5" stroke="#22C55E" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
    <rect x="9" y="17" width="6" height="1.5" rx="0.75" fill="#22C55E" opacity="0.6"/>
  </svg>
</template>
