# Shimeji Neo Technical Context & Architectural Decisions

Based on Deep Research results (Phases 1-4).

## 1. Core Technology Stack
- **Runtime**: Java 21 (LTS) with Preview Features (`--enable-preview`).
- **GC**: Generational ZGC (`-XX:+UseZGC -XX:+ZGenerational`) for low latency.
- **Build System**: Gradle (Kotlin DSL).
- **Native Interop**: Project Panama (Foreign Function & Memory API). JNA is legacy/fallback only.
- **Scripting**: GraalJS (ECMAScript 2024 compliant).
- **GUI**: `java.awt.Window` (Active Rendering). **DO NOT** use `JWindow`, `JFrame`, or JetBrains Compose.

## 2. Phase 1: Native Integration & DPI Handling
### DPI Awareness & Coordinate System
- **Manifest**: Must declare `<dpiAware>true/PM</dpiAware>` and `<dpiAwareness>PerMonitorV2</dpiAwareness>` in the application manifest.
- **Coordinate Conversion**:
  - Win32 APIs return **Physical Pixels**.
  - Java AWT uses **Logical Pixels**.
  - **Solution**: Use `PhysicalToLogicalPointForPerMonitorDPI` (Win32 API) via Panama to convert coordinates accurately. Do not rely on AWT's internal scaling.
- **Work Area Detection**:
  - Use `GetMonitorInfo` for basic work area.
  - Use `SHAppBarMessage` to detect auto-hide taskbars and precise edges.
  - Logic must handle multi-monitor setups with different DPIs.

### Window Management
- **Transparency**: Use `UpdateLayeredWindow` (Win32) with a pre-multiplied ARGB bitmap.
  - Avoid `AWTUtilities` or `setBackground(new Color(0,0,0,0))` on `JWindow` as it conflicts with DWM on Windows 11.
- **Native Access**:
  - Migrate from JNA to **Project Panama** for performance (60 FPS window moves).
  - Use `Arena.ofConfined()` for memory management in the rendering loop.

## 3. Phase 2: Logic & Scripting Architecture
### Scripting Engine (GraalJS)
- **Integration Pattern**: **Shared Engine / Separate Contexts**.
  - Single `Engine` instance to share code cache/JIT optimizations.
  - Separate `Context` per mascot instance for isolation.
- **Sandboxing**:
  - Use `HostAccess.EXPLICIT` to allowlist Java API access.
  - Enforce resource limits (CPU time, memory) to prevent DoS from user scripts.

### AI Architecture
- **Pattern**: **Event-Driven Hierarchical State Machine (HSM)**.
- **Async Handling**: Use **JavaScript Generators (`function*`)** as Coroutines.
  - Allows writing asynchronous logic (e.g., "Walk -> Wait -> Jump") in a synchronous style using `yield`.
  - Java side implements a scheduler to resume generators frame-by-frame.

## 4. Phase 3: Rendering & Performance
### Rendering Pipeline
- **Strategy**: **Active Rendering** via `BufferStrategy`.
  - **Do NOT use**: Swing `paintComponent`, `RepaintManager`, or `Timer`.
  - **Loop**: Custom `while` loop using `System.nanoTime()` on a dedicated thread.
  - **Window**: Use `java.awt.Window` (not `JWindow`) with `setIgnoreRepaint(true)`.
- **Hardware Acceleration**:
  - Prefer Direct3D pipeline (`-Dsun.java2d.d3d=true`).
  - Use `VolatileImage` for VRAM-cached sprites and composition.
  - Implement **Texture Atlas** to reduce texture binding overhead.

### Memory Management
- **Strategy**: Avoid object pooling for small objects (Point, Rect); rely on ZGC. Pool heavy resources (Buffers, Images).

## 5. Phase 4: Packaging & Distribution
- **Tooling**: `jpackage` via **Badass Runtime Plugin** (`org.beryx.runtime`).
- **Runtime Strategy**: **Hybrid Runtime**.
  - Use `jlink` to create a minimal JRE (java.base, java.desktop, etc.).
  - Place non-modular JARs (JNA, App) on the classpath.
- **Installer**: MSI or EXE using **WiX Toolset v3.11**.
  - **Note**: Java 21 `jpackage` is NOT compatible with WiX v4/v5.
- **Launcher Flags**: Embed `--enable-preview` and `--enable-native-access=ALL-UNNAMED` into the launcher via `jpackage` options.
- **Signing**: Use **SignPath.io** for open-source code signing to avoid SmartScreen warnings.