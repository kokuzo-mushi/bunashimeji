package com.group_finity.mascot.animation;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Animationクラスのユニットテスト。
 * 時間管理が内部で行われることを前提に、各機能が正しく動作するかを検証します。
 */
class AnimationTest {

    @Test
    void constructor_shouldCalculateTotalDurationCorrectly() {
        // Arrange
        Pose pose1 = createMockPose("image1.png", 100);
        Pose pose2 = createMockPose("image2.png", 150);
        List<Pose> poses = List.of(pose1, pose2);

        // Act
        Animation animation = new Animation(poses);

        // Assert
        assertEquals(250, animation.getTotalDuration(), "合計デュレーションが正しく計算されていること");
    }

    @Test
    void getPose_shouldReturnFirstPose_whenTimeIsZero() {
        // Arrange
        Pose pose1 = createMockPose("image1.png", 100);
        Pose pose2 = createMockPose("image2.png", 100);
        List<Pose> poses = List.of(pose1, pose2);
        Animation animation = new Animation(poses);

        // Act
        Pose currentPose = animation.getPose();

        // Assert
        assertSame(pose1, currentPose, "時間0の時点では最初のポーズが返されること");
    }

    @Test
    void getPose_shouldReturnCorrectPose_afterTicking() {
        // Arrange
        Pose pose1 = createMockPose("image1.png", 100); // 0-99ms
        Pose pose2 = createMockPose("image2.png", 100); // 100-199ms
        List<Pose> poses = List.of(pose1, pose2);
        Animation animation = new Animation(poses);

        // Act & Assert
        // time = 40ms
        animation.tick(40);
        assertSame(pose1, animation.getPose(), "40ms経過時点ではpose1であるべき");

        // time = 80ms
        animation.tick(40);
        assertSame(pose1, animation.getPose(), "80ms経過時点ではpose1であるべき");

        // time = 120ms
        animation.tick(40);
        assertSame(pose2, animation.getPose(), "120ms経過時点ではpose2に切り替わっているべき");

        // time = 160ms
        animation.tick(40);
        assertSame(pose2, animation.getPose(), "160ms経過時点ではpose2であるべき");
    }

    @Test
    void getPose_shouldLoop_whenTimeExceedsTotalDuration() {
        // Arrange
        Pose pose1 = createMockPose("image1.png", 100); // 0-99ms
        Pose pose2 = createMockPose("image2.png", 100); // 100-199ms
        List<Pose> poses = List.of(pose1, pose2);
        Animation animation = new Animation(poses); // totalDuration = 200ms

        // Act: time = 200ms (5 * 40ms) -> ループして最初に戻る
        for (int i = 0; i < 5; i++) {
            animation.tick(40);
        }

        // Assert
        assertSame(pose1, animation.getPose(), "合計時間を超えたらループして最初のポーズに戻るべき");
    }

    @Test
    void getPose_shouldReturnNull_whenPosesIsEmpty() {
        // Arrange
        Animation animation = new Animation(Collections.emptyList());

        // Act & Assert
        assertNull(animation.getPose(), "ポーズリストが空の場合、nullが返されること");
        assertEquals(0, animation.getTotalDuration(), "ポーズリストが空の場合、合計デュレーションは0であること");
    }

    @Test
    void getPose_shouldReturnFirstPose_whenTotalDurationIsZero() {
        // Arrange
        Pose pose1 = createMockPose("image1.png", 0);
        Animation animation = new Animation(List.of(pose1));

        // Act & Assert
        assertSame(pose1, animation.getPose(), "合計デュレーションが0の場合、常に最初のポーズが返されること");
    }

    private Pose createMockPose(String imageName, int duration) {
        Pose mockPose = mock(Pose.class);
        when(mockPose.getDuration()).thenReturn(duration);
        return mockPose;
    }
}