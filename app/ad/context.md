# Current Development Context: Wall-Ceiling Transition & Refactoring

**Focus:** Implementing smooth transitions between walls and ceilings using "Kinematic Corner Transition" logic, and modernizing the codebase structure.

## 1. Implemented Feature: Smooth Wall-Ceiling Transition

Successfully implemented "Kinematic Corner Transition" logic to handle coordinate mismatches and physics conflicts during transitions.

### Components
- **`CornerMath`**: Utility class for geometric arc calculation.
- **`CornerTurnAction`**: Action class that disables physics (`ignoreWalls`) and moves the mascot along the calculated arc.
- **`ClimbCeilingAction`**: Preparatory action to align the mascot to the wall top (Target Y=128).

### Maintenance & Impact Analysis (Critical for Future Adjustments)

If you change **Anchor Points** or **Ceiling/Wall Detection Logic**, you must update the following:

1.  **Ceiling Anchor Change (e.g., changing `CeilingCrawl` anchor from `64,45`)**:
    -   Update `CornerTurnAction.java`: `ceilingAnchor = new Point(64, 45);` must match the new anchor.
    -   Update `CornerMathTest.java`: Update expected Y values.

2.  **Wall Anchor Change (e.g., changing `Climb` anchor from `64,128`)**:
    -   Update `CornerTurnAction.java`: `wallAnchor = new Point(64, 128);`
    -   Update `ClimbCeilingAction.java`: `TARGET_Y = 128;` (Must match the wall anchor Y).

3.  **Physics/Environment Logic**:
    -   `Main.java`: Ceiling sticking logic (`mascot.setY(envInfo.ceilingY + 10)`) assumes the visual top aligns with the physical ceiling.
    -   `CornerMath.java`: The logic assumes `wallAnchor.y` represents the "depth" from the wall surface.

## 2. Refactoring Roadmap (Mid-to-Long Term)

Based on `Java Desktop App Refactoring Plan.md`.

### Phase 1: Foundation & Data Model
-   **Build System**: Migrate to Gradle (Kotlin DSL).
-   **Data Model**: Introduce `Records` for immutable data (Coordinates, Velocity).
-   **Native**: Prepare FFM API wrapper structure.

### Phase 2: Native Layer Replacement (Project Panama)
-   **Facade**: Implement `WindowsUser32Service` using FFM.
-   **Removal**: Remove JNA dependencies.

### Phase 3: State Machine Reconstruction
-   **Sealed Interfaces**: Define `MascotState`.
-   **Logic Migration**: Replace switch/if-else chains with Pattern Matching.

### Phase 4: MVP Separation
-   **View**: Extract `MascotWindowView`.
-   **Presenter**: Create `MascotPresenter`.
-   **Cleanup**: Remove the "God Class" (`Mascot.java`).

## 3. Asset Naming Convention (Reference)

| Legacy | New Name |
| :--- | :--- |
| shime1.png | Stay1.png |
| shime2.png | Walk2.png |
| shime3.png | Walk4.png |
| shime4.png | Fall1.png |
| shime5.png | Dragged1.png |
| shime6.png | Dragged2.png |
| shime7.png | Dragged2.png |
| shime8.png | Dragged4.png |
| shime9.png | Dragged3.png |
| shime10.png | Dragged5.png |
| shime11.png | Sit1.png |
| shime12.png | Climb3.png |
| shime13.png | WallCling1.png |
| shime14.png | Climb2.png |
| shime15.png | WallCling1.png |
| shime16.png | Climb2.png |
| shime17.png | SlideDown1.png |
| shime18.png | LieDown1.png |
| shime19.png | TripFall1.png |
| shime20.png | LieDown1.png |
| shime21.png | LieDown1.png |
| shime22.png | Jump1.png |
| shime23.png | CeilingStay1.png |
| shime24.png | CeilingCrawl2.png |
| shime25.png | CeilingCrawl1.png |
| shime26.png | Sit1.png |
| shime30.png | Sit1.png |
| shime31.png | Sit1.png |
| shime32.png | Sit1.png |
| shime33.png | Sit1.png |
| shime34.png | Grab1.png |
| shime35.png | Grab2.png |
| shime36.png | Grab4.png |
| shime37.png | Throw1.png |
| shime42.png | Breed1.png |
| shime43.png | Breed2.png |
| :--- | Climb1.png |
| :--- | PullUp1.png |
| :--- | PullUp2.png |
| :--- | ClimbUpForCeiling1.png |