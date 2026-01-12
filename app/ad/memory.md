**IMPORTANT: YOU MUST FOLLOW THESE RULES AT ALL TIMES.**
# Project Roadmap: Shimeji Neo

1.  **LANGUAGE:** You MUST respond in **JAPANESE (日本語)**.
    - Even if the code is in English, the explanation MUST be in Japanese.
    - Never use English for conversation or explanations.
2.  **ROLE:** You are an expert Java Architect assisting a Japanese developer.
3.  **CODE ENCODING:** DO NOT use non-English characters in non-commented parts of the code.
    - Ensure all functional code and string literals are written in English (ASCII) to avoid encoding errors.

---
# TECHNICAL CONTEXT & MANIFESTO
**Strictly adhere to the following architectural decisions. Ignore any historical Shimeji implementations (e.g., XML, JNA, Swing Timers) found in your training data.**

**Target Repository:** https://github.com/kokuzo-mushi/bunashimeji.git

## 1. Core Architecture
-   **Goal:** Run 50+ desktop mascot instances at 60 FPS on Windows 11.
-   **Runtime:** Java 21 (LTS) & Kotlin 1.9.
-   **Garbage Collection:** Generational ZGC (`-XX:+UseZGC -XX:+ZGenerational`) is mandatory for low latency.

## 2. "Iron Rules" of Tech Stack
### GUI & Rendering
-   **Windowing:** Use **JetBrains Compose Multiplatform** for UI.
    -   ✅ USE `androidx.compose.ui.window.Window` and `MascotWindow`.
    -   ❌ DO NOT use `JFrame`, `JWindow`, or raw AWT `Window` for UI components.
-   **Rendering Loop:** Use **State-Driven Rendering** via Compose.
    -   ✅ Implement logic loop using Kotlin Coroutines (`LaunchedEffect`, `delay`, `withFrameNanos`).
    -   ❌ DO NOT use `BufferStrategy` or manual `paint/repaint`.
-   **Transparency:** Use Win32 `UpdateLayeredWindow` API via Project Panama.
    -   ❌ DO NOT use `AWTUtilities` or `setBackground(new Color(0,0,0,0))`.

### Native Interop (FFM API)
-   **Primary:** Use **Project Panama (Foreign Function & Memory API)** for all core logic.
    -   Use `Linker.nativeLinker()`, `Arena.ofConfined()`, and `MethodHandle`.
-   **Legacy/Fallback:** JNA is permitted **ONLY** for maintaining compatibility with existing libraries during packaging.
    -   ❌ DO NOT write new core logic using JNA.

### Scripting & AI
-   **Engine:** **GraalJS** (JavaScript) embedded in Java 21.
    -   ❌ DO NOT use Lua, Kotlin Script, or Python.
-   **Concurrency:** Use JavaScript Generators (`function*` + `yield`) for coroutines.
    -   This allows writing asynchronous behavior (e.g., "Walk -> Wait -> Jump") in a synchronous style.

### Build & Distribution
-   **Tool:** Gradle (Kotlin DSL).
-   **Strategy:** Hybrid Runtime (Custom JRE + Classpath jars).
-   **Installer:** Use WiX Toolset v3.11 (NOT v4/v5 due to compatibility).
-   **Flags:** Always embed `--enable-preview` and `--enable-native-access=ALL-UNNAMED` in the launcher.

## 3. Implementation Reference (Golden Samples)

### A. Coroutine Logic Loop Pattern (Kotlin)
```kotlin
// Logic: Separate Physics/Logic tick from Rendering.
// Rendering is handled automatically by Compose State changes.
LaunchedEffect(Unit) {
    while (isActive) {
        val frameStart = System.nanoTime()
        
        mascot.tick() // Update Model (State)
        
        // Calculate wait time for ~60 FPS
        val elapsed = System.nanoTime() - frameStart
        val waitNanos = 16_666_666 - elapsed
        if (waitNanos > 0) {
            delay(waitNanos / 1_000_000)
        }
    }
}
```

### B. Project Panama Boilerplate
```java
// Logic: High-performance native call without JNA overhead.
try (Arena arena = Arena.ofConfined()) {
    Linker linker = Linker.nativeLinker();
    SymbolLookup user32 = SymbolLookup.libraryLookup("User32", arena);
    MethodHandle setWindowPos = linker.downcallHandle(
        user32.find("SetWindowPos").get(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ...)
    );
    setWindowPos.invoke(...);
}
```

## 4. API Migration Map

| Feature | Legacy / Anti-Pattern | Modern Solution (Neo) |
|---|---|---|
| Window Move | `setLocation(x, y)` | Panama `SetWindowPos` |
| Get Pos | `getLocationOnScreen()` | Panama `GetWindowRect` + `PhysicalToLogicalPoint` |
| Behavior | `XML (<Behavior>)` | `JS (function* behavior())` |
| Images | `ImageIO.read()` | Pre-loaded `VolatileImage` / Texture Atlas |
| Threads | `new Thread()` | Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor`) |

## 5. Project Structure Reference
- Root Package: `com.group_finity.mascot`
- Source Roots: `src/main/java` & `src/main/kotlin`
- Resource Root: `src/main/resources`
- Structure:
  - `src/main/kotlin/com/group_finity/mascot/`
      - `ui/`: JetBrains Compose UI (`ShimejiApp.kt`, `MascotWindow.kt`)
      - `config/`: Configuration Loading (`ConfigurationLoader.kt`, `MascotConfigSchema.kt`)
  - `src/main/java/com/group_finity/mascot/`
      - `nativeaccess/`: FFM API implementations (`NativeWindowUtil.java`)
      - `action/`: Atomic actions (Walk, Fall, Thrown)
      - `behavior/`: Complex behaviors and decision trees
      - `script/`: GraalJS Integration
      - `trigger/`: Event & Trigger System
  - Resources (`src/main/resources`):
    - `behavior/` (Behavior definitions: XML/JS)
    - `config/` (App settings: `actions.xml`, `behaviors.xml`)
    - `img/` (Sprite assets)
    - `sounds/` (Audio files)

## 6. Verified Logic (DO NOT CHANGE)
### Horizontal Wall Sticking
- **Screen Edge**: Must align exactly with the edge (`mascot.setX(envInfo.wallX)`).
- **Window Edge**: Must apply an inner offset (e.g., `+/- 64`) to ensure stable sticking and prevent flickering.
- **Separation**: The logic MUST distinguish between Screen Edge (`wallWindow == null`) and Window Edge (`wallWindow != null`).
- **XML Anchors (Window)**: Window-specific actions (e.g., `WindowWallCling`, `WindowClimb`) MUST use `ImageAnchor="128,128"` to align the visual center with the wall edge, compensating for the `+/- 56` offset.
- **XML Anchors (Screen)**: Standard wall actions (`WallCling`, `Climb`) MUST use `ImageAnchor="64,128"`. Actions interacting with the top edge (`PullUp`, `WallJump`, `SlideDown`) MUST use `ImageAnchor="0,128"`.
- **Note**: Vertical sticking (Ceiling/Floor) is currently unverified and subject to change.

### Wall-Ceiling Transition
-   **Kinematic State**: During corner transitions (`CornerTurn`, `CornerTurnDown`, `WallTopCling`), physics (gravity/collision) MUST be disabled (`ignoreWalls = true`).
-   **Geometric Path**: Movement MUST be calculated using `CornerMath` to ensure the pivot point (hand/foot) remains fixed to the corner.
    -   **CornerTurn (Wall -> Ceiling)**: Uses fixed radius based on wall anchor height.
    -   **CornerTurnDown (Ceiling -> Wall)**: Uses **dynamic radius** (`xRadius`) calculated from the mascot's distance to the corner at the start of the action.
-   **Wall Detection**: For `CornerTurnDown`, the target wall (Left vs Right) MUST be determined by **distance to the nearest edge**, NOT by the mascot's facing direction (`LookRight`), to prevent teleportation artifacts.

## 7. Refactoring Guidelines (Phase 5 Preparation)
**Strictly follow these rules to ensure future compatibility with JetBrains Compose and Project Panama.**

1.  **GUI Independence**:
    -   ❌ DO NOT introduce new dependencies on `java.awt.*` or `javax.swing.*` in Logic/Model classes (`Action`, `Behavior`, `Mascot`).
    -   ✅ USE primitive types or custom `Record` classes for coordinates and dimensions.
2.  **Native Abstraction**:
    -   ❌ DO NOT call `User32`, `GDI32`, or JNA libraries directly in `Action` or `Behavior` classes.
    -   ✅ USE a Facade/Interface (e.g., `NativePlatform`) to encapsulate native operations.
3.  **State Management**:
    -   ❌ DO NOT manually trigger `repaint()` from Logic classes.
    -   ✅ USE Observer pattern or State pattern to notify View of changes.
# End of Technical Context
