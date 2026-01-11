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
-   **Runtime:** Java 21 (LTS) with Preview Features enabled (`--enable-preview`).
-   **Garbage Collection:** Generational ZGC (`-XX:+UseZGC -XX:+ZGenerational`) is mandatory for low latency.

## 2. "Iron Rules" of Tech Stack
### GUI & Rendering
-   **Windowing:** Use `java.awt.Window` directly.
    -   ❌ DO NOT use `JWindow`, `JFrame`, or JetBrains Compose.
-   **Rendering Loop:** Use **Active Rendering** with `BufferStrategy` (2 buffers).
    -   ❌ DO NOT use `paint/repaint`, `Thread.sleep`, or `javax.swing.Timer`.
    -   ✅ Implement a precise `while` loop using `System.nanoTime()`.
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
-   **Tool:** Gradle with `org.beryx.runtime` (Badass Runtime Plugin).
-   **Strategy:** Hybrid Runtime (Custom JRE + Classpath jars).
-   **Installer:** Use WiX Toolset v3.11 (NOT v4/v5 due to compatibility).
-   **Flags:** Always embed `--enable-preview` and `--enable-native-access=ALL-UNNAMED` in the launcher.

## 3. Implementation Reference (Golden Samples)

### A. Active Rendering Loop Pattern
```java
// Logic: Ignore OS repaint events and control the frame rate manually.
setIgnoreRepaint(true);
createBufferStrategy(2);
while (running) {
    long now = System.nanoTime();
    updateState(); // Physics & Logic
    do {
        do {
            Graphics2D g = (Graphics2D) bs.getDrawGraphics();
            g.setComposite(AlphaComposite.Clear); // Clear previous frame
            g.fillRect(0,0,w,h);
            g.setComposite(AlphaComposite.SrcOver);
            drawMascot(g); // Draw current frame
            g.dispose();
        } while (bs.contentsRestored());
        bs.show();
    } while (bs.contentsLost());
    // Sleep logic for 60FPS cap
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
- Source Root: `src/main/java`
- Resource Root: `src/main/resources`
- Structure:
  - `com.group_finity.mascot.Main` (Launcher)
  - `com.group_finity.mascot.trigger.*` (Logic: Event & Trigger System)
  - `com.group_finity.mascot.action.*` (Logic: Actions like Walk, Fall)
  - `com.group_finity.mascot.behavior.*` (Logic: Behavior Definitions)
  - `com.group_finity.mascot.native_interface.*` (Panama: Native Interop)
  - `com.group_finity.mascot.script.*` (Scripting: GraalJS Integration)
  - `com.group_finity.mascot.image.*` (Assets: Image & Texture Management)
  - `com.group_finity.mascot.config.*` (Configuration: XML/YAML Parsers)
  - Resources (`src/main/resources`):
    - `behavior/` (Behavior definitions: XML/JS)
    - `config/` (App settings: `actions.xml`, `system.yaml`)
    - `images/` (Sprite assets)
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
