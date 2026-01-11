/**
 * Example Generator Behavior for Testing
 * 
 * Logic:
 * 1. Turn Right
 * 2. Move Right 5 times (10px each)
 * 3. Wait 3 frames
 * 4. Turn Left
 * 5. Move Left 5 times (10px each)
 */
(function*(mascot) {
    // 1. Turn Right
    mascot.setLookRight(true);
    yield; // Frame 1

    // 2. Move Right (5 frames)
    for (var i = 0; i < 5; i++) {
        mascot.setX(mascot.getX() + 10);
        yield; // Frame 2-6
    }

    // 3. Wait (3 frames)
    for (var i = 0; i < 3; i++) {
        yield; // Frame 7-9
    }

    // 4. Turn Left
    mascot.setLookRight(false);
    // End of generator (implicitly returns undefined, done=true)
})