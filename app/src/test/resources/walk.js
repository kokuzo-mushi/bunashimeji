function* walk() {
    mascot.move(10, 0);
    yield 1; // 1フレーム待機
    mascot.move(10, 0);
    yield 1;
    mascot.move(0, -5);
}