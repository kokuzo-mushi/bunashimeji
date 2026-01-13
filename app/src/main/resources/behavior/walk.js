// グローバル変数の初期化（初回のみ実行される）
if (typeof this.count === 'undefined') {
    this.count = 0;
    this.direction = 1;
}

// エラーログに基づき、Mascot.setAnchorは NeoPoint を要求しているため型定義を取得
if (typeof NeoPoint === 'undefined') {
    var NeoPoint = Java.type('com.group_finity.mascot.type.NeoPoint');
}

// マスコットの状態を取得
var anchor = mascot.getAnchor();

// 20ステップごとに方向転換
if (this.count >= 20) {
    this.direction = -1;
    mascot.setLookRight(false);
} else if (this.count <= -20) {
    this.direction = 1;
    mascot.setLookRight(true);
}

this.count += this.direction;

// 座標更新 (速度: 5)
// NeoPoint (Java Record) 対応: x() メソッドが存在するか確認して使い分ける
var currentX, currentY;

if (typeof anchor.x === 'function') {
    // Java 21 Record (NeoPoint) の場合: アクセサは .x()
    currentX = anchor.x();
    currentY = anchor.y();
} else {
    // 従来の java.awt.Point の場合: アクセサは .x (フィールド) または .getX()
    // GraalJSではフィールドもプロパティとして見える
    currentX = (typeof anchor.getX === 'function') ? anchor.getX() : anchor.x;
    currentY = (typeof anchor.getY === 'function') ? anchor.getY() : anchor.y;
}

var newX = currentX + (this.direction * 5);
// 修正: java.awt.Point ではなく NeoPoint を生成して渡す
mascot.setAnchor(new NeoPoint(Math.round(newX), Math.round(currentY)));