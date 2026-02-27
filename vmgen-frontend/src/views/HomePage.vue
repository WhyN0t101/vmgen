<script setup lang="ts">
import { useRouter } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'

const router = useRouter()
</script>

<template>
  <div class="min-h-screen relative overflow-hidden flex flex-col">
    <!-- Background: diagonal grid + gradient bleed -->
    <div class="absolute inset-0 pointer-events-none">
      <div
        class="absolute inset-0 opacity-[0.06]"
        style="
          background-image:
            linear-gradient(to bottom right, transparent 49.5%, rgba(56, 189, 248, 0.15) 49.5%, rgba(56, 189, 248, 0.15) 50.5%, transparent 50.5%),
            linear-gradient(to top right, transparent 49.5%, rgba(56, 189, 248, 0.12) 49.5%, rgba(56, 189, 248, 0.12) 50.5%, transparent 50.5%);
          background-size: 64px 64px;
        "
      />
      <div
        class="absolute top-0 right-0 w-[80vmax] h-[80vmax] rounded-full opacity-30"
        style="background: radial-gradient(circle, rgba(56, 189, 248, 0.12) 0%, transparent 70%); transform: translate(30%, -30%);"
      />
    </div>

    <div class="relative z-10 flex-1 flex items-center px-6 py-16 sm:px-8 md:px-12 lg:px-16">
      <div class="w-full max-w-5xl">
        <!-- Wordmark: the one thing they remember -->
        <div class="hero-reveal hero-reveal-1 mb-2">
          <h1
            class="font-mono text-5xl sm:text-6xl md:text-7xl lg:text-8xl font-semibold tracking-tight text-slate-100"
            style="text-shadow: 0 0 60px rgba(56, 189, 248, 0.15);"
          >
            VMGEN
          </h1>
          <div
            class="mt-2 h-1 w-24 rounded-full bg-sky-400/90"
            style="box-shadow: 0 0 20px rgba(56, 189, 248, 0.5);"
          />
        </div>

        <!-- Tagline -->
        <p class="hero-reveal hero-reveal-2 mt-6 text-xl sm:text-2xl md:text-3xl text-slate-300 font-light tracking-tight max-w-xl">
          Build vulnerable training environments.
        </p>

        <!-- Value + CTAs in a panel (asymmetric: sits under the wordmark, not centered) -->
        <div class="hero-reveal hero-reveal-3 mt-10 sm:mt-12">
          <div class="app-panel rounded-2xl p-6 sm:p-8 md:p-10 max-w-2xl">
            <p class="hero-reveal hero-reveal-4 text-slate-400 text-sm sm:text-base leading-relaxed">
              Deterministic VM generation for cybersecurity education:
              <span class="text-sky-300/95 font-medium">reproducible</span>,
              <span class="text-sky-300/95 font-medium">traceable</span>, and
              <span class="text-sky-300/95 font-medium">portable</span>.
            </p>
            <div class="hero-reveal hero-reveal-5 mt-8 flex flex-col sm:flex-row gap-4">
              <BaseButton
                size="lg"
                class="text-base px-6 py-3 w-full sm:w-auto"
                @click="router.push({ name: 'generate' })"
              >
                Generate VM
              </BaseButton>
              <BaseButton
                variant="ghost"
                size="lg"
                class="text-base px-6 py-3 w-full sm:w-auto border border-slate-600/80 hover:border-slate-500 hover:bg-slate-800/60"
                @click="router.push({ name: 'dashboard' })"
              >
                Dashboard
              </BaseButton>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
