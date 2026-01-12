function* walk() {
    mascot.setX(mascot.getX() + 10);
    yield 1; // 1フレーム待機
    mascot.setX(mascot.getX() + 10);
    yield 1;
    mascot.setY(mascot.getY() - 5);
}