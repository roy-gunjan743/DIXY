# Evidence: Pinning-Correlated Deadlock Detection — End-to-End Pass

**Date:** 2026-07-27
**Replaces:** `evidence/2026-07-19-e2e-pinning-deadlock-pass/`

## Why the old evidence was replaced

The 2026-07-19 folder captured a passing run of `bothEdgeTypesCoexistInOneLiveScenario`,
which only asserted that a `PINS` edge and a `WAITS_FOR`/`HOLDS` edge could exist
*somewhere* in the same graph. It did not assert that the detector actually
correlates a real deadlock cycle with a real pinning event — the core claim
of the invention. This folder documents the stronger test that does.

## What this proves

`HybridPinningDeadlockEndToEndTest.detectsHybridCycleWithPinningCorrelation()`
runs, through the real production pipeline (JVMTI bridge → JFR → `EventCollector`
→ `WaitForGraph` → `DeadlockDetector`), two things concurrently in one JVM:

1. A **real, permanent platform-thread deadlock** — two `Thread.ofPlatform()`
   threads deadlocked on `lockA`/`lockB` via ordinary `synchronized` blocks.
2. A **real, concurrent virtual-thread pin** — a `Thread.ofVirtual()` thread
   holding a monitor (`pinMonitor`) long enough to pin its carrier.

`DeadlockDetector.detect()` finds the deadlock cycle from graph structure
(DFS back-edge detection), and separately `correlatePinning()` attaches any
sufficiently-aged `PINS` edge anywhere in the graph to that cycle by time,
not by structural graph position. The test asserts both: a cycle was found,
and `DeadlockCycle.hasPinningCorrelation()` is true for it.

Sample detector output from a passing run:

```
======================================
      DEADLOCK DETECTED
======================================
Pinning-Correlated Deadlock Cycle:
  -> Monitor-174472571 --[HOLDS]--> deadlock-thread-A
  -> deadlock-thread-A --[WAITS_FOR]--> Monitor-1325488275
  -> Monitor-1325488275 --[HOLDS]--> deadlock-thread-B
  -> deadlock-thread-B --[WAITS_FOR]--> Monitor-174472571
  [Correlated pinning — a thread in this cycle was ALSO pinned]:
    -> concurrent-pinner --[PINS]--> carrier--1
```

Full raw logs from both environments are in this folder:

- `local-windows-test-run.log` — `.\gradlew.bat clean test` on the Windows dev machine (BUILD SUCCESSFUL in 55s)
- `ci-linux-test-run.log` — GitHub Actions `build-and-test` job on `ubuntu-latest`, run #16, commit `eb9b143` (BUILD SUCCESSFUL in 2m 14s)
- `ci-run-url.txt` — link to the Actions run

All four test classes passed in both environments:

- `ClassicDeadlockEndToEndTest`
- `HybridPinningDeadlockEndToEndTest`
- `VirtualThreadPinningEndToEndTest`
- `VirtualDeadlockDetectionApplicationTests`

## Design note: why the pinned thread isn't structurally in the cycle

`correlatePinning()` deliberately correlates by **time**, not by requiring the
pinned thread to sit inside the deadlock's own wait-for edges. This isn't a
simplification — it's necessary, because of two independent JDK observability
gaps discovered during this verification pass. Both are worth citing directly
in the Phase 2 invention disclosure as justification for why the native JVMTI
bridge component is necessary rather than incidental.

### Gap 1: Carrier-thread misattribution

`ThreadStatePoller` (built on `ManagementFactory.getThreadMXBean().dumpAllThreads()`)
sees a virtual thread's block, but the JDK's own `Thread.State` reporting for
a pinned virtual thread attributes the block to the **carrier** platform
thread hosting it, not to the virtual thread itself. A naive structural
correlation would misattribute the wait.

### Gap 2: `ThreadMXBean` doesn't enumerate virtual threads at all

`dumpAllThreads()` only enumerates **platform** threads. Virtual threads are
not tracked in any `ThreadGroup`, by Project Loom's own design (to avoid
overwhelming thread-management APIs when there may be millions of them). At
the same time, JFR's `jdk.JavaMonitorEnter` event only commits on a
**successful** monitor acquisition — it never fires for a block that never
resolves. The combined effect: a virtual thread's own *permanent* block has
no observable signal at all, through either public mechanism, anywhere in
this pipeline today.

**Practical consequence:** production-grade detection of virtual-thread-side
blocking cannot be built on `ThreadMXBean` or JFR alone — it genuinely
requires the native JVMTI bridge to poll virtual thread state directly. That
native-bridge requirement is a direct, citable consequence of these two gaps,
not an implementation convenience.

## Files in this folder

- `README.md` — this file
- `local-windows-test-run.log` — full local Windows test run
- `ci-linux-test-run.log` — full GitHub Actions CI run (Linux)
- `ci-run-url.txt` — link to the CI run
