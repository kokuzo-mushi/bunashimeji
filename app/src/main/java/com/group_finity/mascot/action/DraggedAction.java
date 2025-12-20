package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
import com.group_finity.mascot.config.xml.XmlAnimation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ドラッグ中のアクション。
 * マウスの移動速度に合わせて、定義されたポーズの中から適切なものを選択して表示します。
 * <p>
 * XML定義の &lt;Animation&gt; 内のポーズ順序は以下の通りと仮定します:
 * 1. 左・高速移動
 * 2. 左・低速移動
 * 3. 静止
 * 4. 右・低速移動
 * 5. 右・高速移動
 */
public class DraggedAction implements Action {

    private final List<Animation> poseAnimations = new ArrayList<>();
    private int previousX;
    private int previousY;
    private boolean firstFrame = true;
    private boolean finished = false;

    // 速度の閾値 (ピクセル/フレーム)
    private static final int SPEED_FAST = 15;
    private static final int SPEED_SLOW = 2;

    public DraggedAction(XmlAnimation xmlAnimation) {
        if (xmlAnimation != null) {
            // XMLに定義されたポーズを順番に読み込み、それぞれ単独のアニメーションとして保持する
            for (var xmlPose : xmlAnimation.getPoses()) {
                Pose pose = new Pose(xmlPose.getImage(), xmlPose.getDuration(), xmlPose.getImageAnchorPoint());
                Animation anim = new Animation(List.of(pose));
                // 初期化のために一度tickしておく（ポーズを確定させるため）
                anim.tick(0);
                this.poseAnimations.add(anim);
            }
        }
    }

    @Override
    public void execute(Mascot mascot) {
        int currentX = mascot.getX();
        int currentY = mascot.getY();
        final int FRAME_DURATION_MS = 40; // 1フレームの時間(ms)

        if (firstFrame) {
            previousX = currentX;
            previousY = currentY;
            firstFrame = false;
        }

        // X方向の移動速度を計算
        int vx = currentX - previousX;
        int vy = currentY - previousY;
        
        previousX = currentX;
        previousY = currentY;

        // ポーズ選択ロジック
        if (poseAnimations.size() >= 5) {
            Animation targetAnimation;
            if (vx < -SPEED_FAST) {
                targetAnimation = poseAnimations.get(0); // 左・速
            } else if (vx < -SPEED_SLOW) {
                targetAnimation = poseAnimations.get(1); // 左・遅
            } else if (vx > SPEED_FAST) {
                targetAnimation = poseAnimations.get(4); // 右・速
            } else if (vx > SPEED_SLOW) {
                targetAnimation = poseAnimations.get(3); // 右・遅
            } else {
                targetAnimation = poseAnimations.get(2); // 静止
            }
            mascot.setAnimation(targetAnimation);
        } else if (!poseAnimations.isEmpty()) {
            // フォールバック: 定義不足の場合は最初のポーズを使用
            mascot.setAnimation(poseAnimations.get(0));
        }

        // If the mascot is no longer being dragged, finish this action.
        if (!mascot.isBeingDragged()) {
            // 慣性を適用するために、現在の速度をマスコットに設定する
            // FallActionなどがこの速度を引き継いで動作する
            mascot.setVelocityX(vx);
            mascot.setVelocityY(vy);
            finished = true;
        }
    }

    @Override
    public boolean hasNext() {
        return !finished;
    }

    @Override
    public void reset() {
        this.finished = false;
        this.firstFrame = true;
    }
}