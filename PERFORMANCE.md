# Performance targets

This document defines provisional targets and measurement rules. It does not
claim that the current foundation build meets them. Evidence will be produced by
issue #32 and the bot/load work in epic #17.

## Client hardware profiles

### Compatibility profile

- Reference class: Intel UHD 620, AMD Vega 8, or measured equivalent.
- Resolution: 1280×720.
- Preset: Low.
- Target: 30 FPS with p95 frame time no greater than 33.3 ms in the approved
  representative gameplay scene.
- Memory: 8 GiB system RAM minimum, including memory shared with the iGPU.

### Primary integrated-GPU profile

- Reference class: Intel Iris Xe 80 EU, AMD Radeon 660M/680M, or a newer
  integrated GPU with measured equivalent or better behavior.
- Resolution: 1920×1080.
- Preset: Low or Medium, depending on the first-run benchmark.
- Target: 60 FPS with p95 frame time no greater than 16.7 ms in the approved
  representative gameplay scene.
- Memory: 16 GiB system RAM recommended.

### Discrete profile

High settings may use additional shadows, vegetation, effects, and resolution,
but gameplay readability and network behavior must remain identical. No feature
required to understand combat, construction, projectiles, team boundaries, or
the deathmatch zone may be exclusive to High.

## Graphics API baseline

The intended baseline is OpenGL 3.3 core or another explicitly supported backend
with equivalent required features. Ray tracing, mesh shaders, variable-rate
shading, and vendor-specific extensions are optional enhancements, never launch
requirements.

Exact operating systems, drivers, JVM, jMonkeyEngine version, and device models
must accompany every published benchmark result.

## First-run benchmark

Automatic quality selection uses measured frame behavior rather than a table of
GPU names. A short deterministic scene should include:

- representative terrain and central walls;
- four team sectors and typical long sight lines;
- character and weapon animation load;
- a realistic number of player-built structures;
- vegetation, particles, projectiles, lights, shadows, and UI;
- representative texture residency and material variety.

The benchmark records warm-up separately and reports at least median, p95, and
p99 CPU/GPU frame time, frame pacing, peak committed memory, texture memory
where observable, draw calls, triangles, active lights, shadow casters, particles,
and visible construction count.

The result chooses a conservative preset. The user can always override it, rerun
the benchmark, or disable automatic quality changes.

## Quality presets

Presets control bounded presentation features, including:

- internal render scale and dynamic-resolution range;
- shadow resolution, cascades, distance, and update frequency;
- terrain/object LOD thresholds;
- vegetation density and animation distance;
- particles, decals, ambient effects, and effect pooling;
- post-processing quality and optional effects;
- texture size/streaming budget and anisotropic filtering;
- reflection and water quality;
- maximum dynamic lights and per-object light influence.

Low must preserve silhouettes, hit feedback, projectile visibility, construction
state, interactable-resource state, protected boundaries, and dangerous zones.
It must not gain performance by hiding authoritative gameplay information.

## Adaptive quality

Any runtime adaptation uses hysteresis and long-enough observation windows to
avoid oscillation. It may lower render scale or selected visual effects after a
sustained budget violation. It must not change server simulation, collision,
hitboxes, field of view limits used for fairness, or network update frequency.

Automatic quality recovery is slower than degradation and is capped by the
user-selected maximum preset.

## Asset and map budgets

The reference map and all user-map validation rules need measurable budgets for
at least:

- total and visible triangles;
- draw calls and material switches;
- unique textures, dimensions, formats, and resident bytes;
- skeletal meshes and bones;
- lights and shadow casters;
- particles and decals;
- collision mesh complexity;
- number and LOD of dynamic constructions;
- shader variants and compilation behavior.

The Low profile is a first-class shipping target. A map that only performs on a
discrete GPU does not satisfy the default format expectations unless explicitly
marked as requiring a higher hardware tier and rejected or warned about by
servers/clients configured for the baseline.

## Server performance is separate

Client FPS targets do not prove server capacity. The dedicated server has its
own fixed-tick, CPU, memory, GC, I/O, and bandwidth budgets. Forty-player support
is accepted only after real-action bot scenarios and soak tests report tick-time
percentiles, queue depth, memory behavior, GC pauses, and network traffic.

## Reproducible benchmark report

A result is only comparable when it records:

- Git commit and dirty-state flag;
- client/server build and protocol versions;
- map ID/version/hash;
- asset-pack ID/version/hash;
- OS, kernel/build, CPU, GPU, driver, RAM, and JVM;
- resolution, render scale, preset, and every non-default override;
- scenario seed, duration, warm-up, bot/player count, and camera path;
- median/p95/p99 metrics and raw machine-readable output.

Screenshots and average FPS alone are not acceptable performance evidence.

## Release acceptance

Before 1.0, the compatibility target must be manually measured on at least one
approved Intel/AMD low-end integrated device, and the primary target on at least
one Intel Iris Xe-class and one AMD 660M/680M-class device or documented measured
equivalents. Results that fail remain defects or cause the published minimum
specification to be changed explicitly; they are not hidden by lowering the
reporting standard.
