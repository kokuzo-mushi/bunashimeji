# Current Development Context: Security Hardening & Refactoring Preparation

**Focus:** Implementing security measures (XXE/Zip Slip prevention) and preparing the codebase for Phase 5 modernization (GUI/Native replacement).

## 1. Implemented Feature: Wall-Ceiling Transition (Completed)

Successfully implemented "Kinematic Corner Transition" logic to handle coordinate mismatches and physics conflicts during transitions.

### Components
- **`CornerMath`**: Utility class for geometric arc calculation.
- **`CornerTurnAction` / `CornerTurnDownAction`**: Actions that disable physics (`ignoreWalls`) and move the mascot along the calculated arc.
- **`WallTopClingAction`**: Action for clinging to the top of the wall, ignoring physics to prevent jitter.
- **`CeilingCrawlAction`**: Updated to handle wall collisions by stopping or turning, preventing infinite loops.

### Key Logic
- **Hybrid Physics**: Physics (gravity/collision) is temporarily disabled during transition actions to allow geometric path following.
- **Anchor Point Correction**: `CornerMath` calculates the visual anchor position to ensure the pivot point (e.g., hand) stays fixed on the corner.

## 2. Refactoring Roadmap (Updated)

### Phase 4: Feature Enhancement & Optimization (Current)
-   **Security Hardening**:
    -   [ ] **XXE Prevention**: Secure XML parser configuration.
    -   [ ] **Zip Slip Prevention**: Path validation during zip extraction.
-   **Interactive Actions**:
    -   [ ] **Pull Up**: Climbing up from the bottom of a window.
    -   [ ] **Teeter**: Balancing on the edge.
-   **Data Model Cleanup**: Introduce `Records` for immutable data.

### Phase 5: Next-Gen Architecture (Future)
-   **GUI Replacement**: Migrate from Swing/AWT to **JetBrains Compose Multiplatform**.
-   **Native Layer Replacement**: Migrate from JNA to **Project Panama**.
-   **Architecture**: Implement MVP (Model-View-Presenter) pattern.

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