<script setup lang="ts">
// Static informational page,   no data fetching required
</script>

<template>
  <div class="max-w-3xl mx-auto px-6 py-10">
    <div class="app-panel rounded-2xl px-6 py-6 md:px-10 md:py-8 space-y-10">

      <header class="space-y-2 border-b border-slate-800/70 pb-6">
        <p class="text-[11px] font-mono tracking-[0.3em] uppercase text-slate-500">
          VMGen // Platform Information
        </p>
        <h1 class="text-3xl font-semibold text-slate-50">Trust Model</h1>
        <p class="text-sm text-slate-400">
          This page documents the trust boundaries and integrity guarantees of the VMGen platform.
          Read this before downloading or submitting artefacts.
        </p>
      </header>

      <!-- Trust boundary -->
      <section class="space-y-3">
        <h2 class="text-lg font-semibold text-slate-100">Platform trust boundary</h2>
        <p class="text-sm text-slate-400 leading-relaxed">
          The platform exercises full control over the build pipeline up to and including OVA export.
          This covers template cloning, script execution, flag derivation and placement, network
          isolation, and provenance assembly. Once an OVA is downloaded, it executes on
          infrastructure outside platform control. The platform cannot enforce or verify runtime
          integrity after export.
        </p>
        <div class="rounded-xl border border-slate-700/50 bg-slate-800/40 px-5 py-4 text-sm text-slate-300 space-y-2">
          <p class="font-medium">What the platform guarantees</p>
          <ul class="list-disc list-inside space-y-1 text-slate-400 text-sm">
            <li>The OVA was produced from the registered template and script versions recorded in the provenance manifest.</li>
            <li>Flags were derived using HMAC-SHA256 keyed with a platform secret that never leaves the API server.</li>
            <li>The build network was isolated,   build VMs could not reach the backend API, the database, or other builds.</li>
            <li>The exported OVA SHA-256 hash matches the value in the provenance manifest and the download response header.</li>
          </ul>
        </div>
        <div class="rounded-xl border border-amber-500/20 bg-amber-500/5 px-5 py-4 text-sm text-amber-300 space-y-2">
          <p class="font-medium">What the platform does not guarantee</p>
          <ul class="list-disc list-inside space-y-1 text-amber-400/80 text-sm">
            <li>Integrity of the VM after it is imported into a third-party hypervisor.</li>
            <li>Availability of external package repositories used during provisioning.</li>
            <li>Identical low-level artefacts across builds,   only functional equivalence (same vulnerability, same exploitation requirements) is guaranteed for identical inputs.</li>
          </ul>
        </div>
      </section>

      <!-- Provenance and integrity verification -->
      <section class="space-y-3">
        <h2 class="text-lg font-semibold text-slate-100">Artefact integrity verification</h2>
        <p class="text-sm text-slate-400 leading-relaxed">
          Each build produces a provenance manifest (JSON) that records the template version and
          SHA-256 hash, script versions and hashes, build seed, flag derivation timestamp, and the
          SHA-256 of the exported OVA. To independently verify a downloaded OVA:
        </p>
        <ol class="list-decimal list-inside space-y-1 text-sm text-slate-400">
          <li>Download the provenance manifest from the build status page.</li>
          <li>Recompute <code class="bg-slate-800 px-1 rounded font-mono text-xs">sha256sum &lt;filename&gt;.ova</code> locally.</li>
          <li>Compare the result against <code class="bg-slate-800 px-1 rounded font-mono text-xs">ovaSha256</code> in the manifest.</li>
        </ol>
        <p class="text-sm text-slate-500">
          The SHA-256 is also returned in the <code class="bg-slate-800 px-1 rounded font-mono text-xs">X-Checksum-SHA256</code> HTTP response header during download for automated verification.
        </p>
      </section>

      <!-- Telemetry -->
      <section class="space-y-3">
        <h2 class="text-lg font-semibold text-slate-100">Guest telemetry</h2>
        <p class="text-sm text-slate-400 leading-relaxed">
          When telemetry is enabled at build time, a collector script is placed inside the VM.
          The student runs this script manually after exploitation; it captures recent shell history
          and writes a JSON report to <code class="bg-slate-800 px-1 rounded font-mono text-xs">/tmp/vmgen-telemetry.json</code>.
          The student then uploads this file via the build status page.
        </p>
        <div class="rounded-xl border border-amber-500/20 bg-amber-500/5 px-5 py-4 text-sm text-amber-300">
          Telemetry is entirely self-reported. The platform accepts the uploaded JSON as-is and
          cannot verify that it was produced by the collector script, that it reflects genuine
          exploitation activity, or that it has not been modified. Instructors should treat
          telemetry as indicative evidence only, not as proof of exploitation methodology.
        </div>

        <!-- Telemetry data policy -->
        <div class="rounded-xl border border-slate-700/50 bg-slate-800/40 px-5 py-4 text-sm text-slate-300 space-y-3">
          <p class="font-medium">Telemetry data policy</p>
          <ul class="list-disc list-inside space-y-1 text-slate-400 text-sm">
            <li><span class="text-slate-300">What is collected:</span> the JSON payload you upload — typically a shell-history excerpt produced by the collector script. No other guest data is captured by the platform.</li>
            <li><span class="text-slate-300">Who can access it:</span> you (via the build status page) and platform administrators for operational support and abuse investigation. Telemetry is not shared with third parties.</li>
            <li><span class="text-slate-300">Retention:</span> uploaded telemetry is retained until you delete it or until the platform's automatic 90-day retention policy removes it, whichever comes first.</li>
            <li><span class="text-slate-300">Opt-in only:</span> telemetry collection is disabled by default and must be explicitly enabled at build creation time. Builds without telemetry enabled cannot upload data.</li>
          </ul>
          <p class="text-slate-500 text-xs">
            You may delete your uploaded telemetry at any time from the build status page.
            Deletion is immediate and permanent.
          </p>
        </div>
      </section>

      <!-- Flag submission -->
      <section class="space-y-3">
        <h2 class="text-lg font-semibold text-slate-100">Flag submission</h2>
        <p class="text-sm text-slate-400 leading-relaxed">
          Flag validation is proof of possession, not proof of exploitation methodology. The
          platform verifies that the submitted value matches the expected flag for that build using
          constant-time comparison. It does not distinguish between intended exploitation,
          filesystem inspection, memory analysis, or any other extraction method. Distinguishing
          between methods is the responsibility of instructors or external assessment frameworks.
        </p>
      </section>

      <!-- Reproducibility -->
      <section class="space-y-3 border-t border-slate-800/70 pt-6">
        <h2 class="text-lg font-semibold text-slate-100">Reproducibility scope</h2>
        <p class="text-sm text-slate-400 leading-relaxed">
          Given identical platform-controlled inputs (template version, script versions, parameters,
          and random seed), the platform produces functionally equivalent VMs that expose identical
          vulnerabilities and require identical exploitation techniques. Flags are explicitly excluded
          from reproducibility,   they incorporate a generation timestamp to ensure uniqueness across
          builds. Bit-for-bit identical OVA files are not guaranteed.
        </p>
        <p class="text-sm text-slate-500">
          Known sources of non-determinism outside the functional equivalence definition include:
          external package repository availability, timing-dependent script behaviour, and
          hypervisor-assigned hardware identifiers.
        </p>
      </section>

    </div>
  </div>
</template>
