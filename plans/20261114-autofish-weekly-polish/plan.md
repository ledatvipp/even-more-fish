---
title: Auto Fishing weekly allowance and native cinematic polish
status: completed
priority: P1
effort: medium
branch: current-local-worktree
tags: [autofish, native-fishing, sftp]
created: 2026-11-14
---

Scope: improve native Auto Fishing, add permission-based weekly allowance and compact lore, polish native cinematics and add a local exceptional-catch burst. Upload only the EMF JAR to Skyblock and BoxPvP. Preserve other JARs, runtime configuration and existing local source changes. Simulator flags remain unchanged.

- [x] Inspect existing service, profile/store, menu, event acceptance, native effects and all command variants.
- [x] Implement allowance, persistence, permission metadata, lore and cosmetic changes.
- [x] [Verify, review and deploy](phase-01-verify-and-deploy.md).

Acceptance: Vietnam Monday reset; highest permission wins; purchased/trial balance preserved; usage survives save/load; no world-wide hook scan; accepted catches only; bounded cosmetic broadcast; Gradle/JUnit and required headless checks; JAR hash readback on both servers. Client visuals and server startup require a subsequent clean restart and live acceptance.
