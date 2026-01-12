(function*() {
    // 右へ移動 (X+20)
    mascot.move(20, 0);
    yield 10; // 10フレーム待機

    // 待機
    yield 20;

    // 左へ戻る (X-20)
    mascot.move(-20, 0);
})
