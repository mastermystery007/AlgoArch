# AlgoArch

A gamified Android app for learning **data structures & algorithms** and **system design**, built for interview prep on a phone.

Instead of typing code on a tiny keyboard, you *reason* about it with quick, tappable challenges, then put your architecture skills to the test in a traffic simulator.

## Gameplay

| Challenge | What you do |
|---|---|
| **Complexity duels & pattern spotting** | Pick the Big O of a snippet, or the pattern (two pointers, monotonic stack, DP…) that cracks a problem. |
| **Build the code** | Parsons problems: tap shuffled lines into the right order, avoiding a sneaky buggy line. |
| **Trace it** | Tap graph or tree nodes in the order BFS, DFS, Dijkstra, Kahn's algorithm or a traversal visits them. |
| **System design sandbox** | Add load balancers, app servers, caches, CDNs, queues, shards and replicas, then simulate traffic. Tiers light up red when they buckle. Ship it when it holds under budget and the latency target. |

**Progression:** two tracks (Algorithms and Architecture) on a world map of regions. Levels unlock in order; clearing a region unlocks the next. Boss levels mix everything.

**Game feel:** XP and player levels, 5 hearts that refill over time, daily streaks, 1-3 stars per level, and achievements.

**Spaced repetition:** every missed question becomes a *rematch* that comes back after 1, 3, 7 and 14 days until it sticks. Rematches are free and restore a heart.

## Content

| Algorithms | Architecture |
|---|---|
| 🏝️ Arrays Archipelago: Big O, hash maps | 🏙️ Scaling Shores: load balancing, caching |
| ⛰️ Two-Pointer Peaks: two pointers, sliding window | 🌐 Edge & Replicas: CDNs, replication, CAP |
| 🏜️ Stack Canyon: stacks, queues, monotonic stacks | 🗄️ Data Depths: sharding, queues, rate limiting |
| 🌊 Binary Search Bay | 🏰 The Citadel: boss round |
| 🌳 Tree Grove | |
| 🌲 Graph Forest | |
| 🌋 DP Volcano | |

All content lives in JSON packs under `app/src/main/assets/content/`, so you can add regions without touching code. Each file is one region:

```json
{ "id": "heaps", "order": 8, "track": "algo", "title": "Heap Harbor", "emoji": "⚓",
  "description": "...", "levels": [ { "id": "heaps-1", "title": "...", "challenges": [ ... ] } ] }
```

Challenge types are `choice`, `order`, `trace` and `design`; see existing packs for the fields. After editing content, run:

```bash
python3 tools/validate_content.py
```

It checks ids and answer indices, **re-runs every trace algorithm** to confirm the stored answer, and brute-forces every design scenario to make sure it is solvable (and that the starting setup fails). `tools/sim.py` mirrors `DesignSimulator.kt` for tuning scenarios.

## Tech

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- kotlinx.serialization for content packs, DataStore for player progress (offline-first, no backend)
- Pure-Kotlin game rules (`game/Rules.kt`) and traffic model (`game/DesignSimulator.kt`) with unit tests
- minSdk 26, targetSdk 35

## Build

Open the project in Android Studio and run the `app` configuration, or:

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # rules, simulator and content tests
```

GitHub Actions builds the debug APK and runs the tests on every push; the APK is attached to each run as an artifact.

## Project layout

```
app/src/main/java/com/algoarch/app/
  data/        content models, JSON loading, progress storage
  game/        Rules (XP, hearts, streaks, rematches, achievements), DesignSimulator
  ui/home      world map
  ui/game      play session + challenge views (choice, order, trace, design)
  ui/profile   stats and achievements
app/src/main/assets/content/   region JSON packs
tools/                         content validator and simulator twin
```
