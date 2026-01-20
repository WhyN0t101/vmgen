<script setup lang="ts">
import { useRouter } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'

const router = useRouter()
</script>

<template>
  <div class="min-h-screen relative overflow-hidden">
    <!-- Animated background grid -->
    <div class="absolute inset-0 opacity-20">
      <div class="absolute inset-0" style="background-image: linear-gradient(rgba(14, 165, 233, 0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(14, 165, 233, 0.1) 1px, transparent 1px); background-size: 50px 50px;"></div>
    </div>
    
    <!-- Main content -->
    <div class="relative z-10 min-h-screen flex flex-col">
      <!-- Hero section -->
      <div class="flex-1 flex items-center justify-center px-6 py-20">
        <div class="max-w-4xl mx-auto text-center">
          <!-- Logo/Brand -->
          <div class="mb-8 flex justify-center">
            <div class="relative">
              <div class="absolute inset-0 bg-primary-500/20 blur-3xl rounded-full"></div>
              <div class="relative bg-slate-900/80 backdrop-blur-sm border border-primary-500/30 rounded-2xl p-6">
                <svg class="w-16 h-16 text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
              </div>
            </div>
          </div>
          
          <!-- Headline -->
          <h1 class="text-6xl md:text-7xl font-bold text-slate-100 mb-6 leading-tight">
            Build
            <span class="text-primary-500">Vulnerable</span>
            <br />
            Training Environments
          </h1>
          
          <!-- Value proposition -->
          <p class="text-xl md:text-2xl text-slate-300 mb-10 max-w-2xl mx-auto leading-relaxed">
            Deterministic VM generation for cybersecurity education. 
            <span class="text-primary-400">Reproducible</span>, 
            <span class="text-primary-400">traceable</span>, and 
            <span class="text-primary-400">portable</span>.
          </p>
          
          <!-- Single CTA -->
          <div class="flex flex-col sm:flex-row gap-4 justify-center items-center">
            <BaseButton size="lg" class="text-lg px-8 py-4" @click="router.push({ name: 'generate' })">
              Generate Your First VM
            </BaseButton>
            <BaseButton 
              variant="ghost" 
              size="lg" 
              class="text-lg px-8 py-4"
              @click="router.push({ name: 'dashboard' })"
            >
              View Dashboard
            </BaseButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
