package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.action.AnimateAction;
import com.group_finity.mascot.action.MoveAction;
import com.group_finity.mascot.action.SequenceAction;
import com.group_finity.mascot.action.WalkAction;
import com.group_finity.mascot.action.TurnAction;
import com.group_finity.mascot.action.JumpAction;
import com.group_finity.mascot.action.FallAction;
import com.group_finity.mascot.action.DraggedAction;
import com.group_finity.mascot.action.ChaseAction;
import com.group_finity.mascot.action.ClimbAction;
import com.group_finity.mascot.action.CeilingCrawlAction;
import com.group_finity.mascot.action.SlideDownAction;
import com.group_finity.mascot.action.WallJumpAction;
import com.group_finity.mascot.action.WallClingAction;
import com.group_finity.mascot.action.LieDownAction;
import com.group_finity.mascot.action.RandomChoiceAction;
import com.group_finity.mascot.action.StayAction;
import com.group_finity.mascot.action.BreedAction;
import com.group_finity.mascot.action.DigAction;
import com.group_finity.mascot.action.GatherAction;
import com.group_finity.mascot.config.xml.XmlPose;
import com.group_finity.mascot.config.xml.XmlAction;
import com.group_finity.mascot.config.xml.XmlActionReference;
import com.group_finity.mascot.config.xml.XmlActions;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a map of named {@link Action}s from an XML configuration file.
 * <p>
 * It would parse an XML file like {@code actions.xml}, instantiating action
 * classes by reflection and configuring them with parameters defined in the file.
 * <p>
 * Example XML:
 * <pre>{@code
 * <actions>
 *   <action name="Walk" class="com.group_finity.mascot.action.WalkAction">
 *     <param name="duration" value="200" />
 *   </action>
 * </actions>
 * }</pre>
 */
public class ActionBuilder {

    public Map<String, Action> build(Path actionsPath) {
        try {
            JAXBContext context = JAXBContext.newInstance(XmlActions.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            XmlActions xmlActions = (XmlActions) unmarshaller.unmarshal(actionsPath.toFile());

            if (xmlActions.getActions() == null) {
                return Collections.emptyMap();
            }

            Map<String, Action> builtActions = new HashMap<>();
            for (XmlAction xmlAction : xmlActions.getActions()) {
                // この時点では、参照を持つアクション（例: Sequence）は不完全な状態で生成されます。
                Action action = createAction(xmlAction);
                if (action != null) {
                    builtActions.put(xmlAction.getName(), action);
                }
            }

            // すべてのActionインスタンスが生成された後、Action間の参照を解決します。
            for (XmlAction xmlAction : xmlActions.getActions()) {
                Action action = builtActions.get(xmlAction.getName());
                // ここでは仮に SequenceAction というクラスを想定しています。
                if (action instanceof SequenceAction) {
                    resolveSequenceAction((SequenceAction) action, xmlAction, builtActions);
                } else if (action instanceof RandomChoiceAction) {
                    // RandomChoiceAction の参照解決
                    resolveRandomChoiceAction((RandomChoiceAction) action, xmlAction, builtActions);
                }
            }

            return Collections.unmodifiableMap(builtActions);

        } catch (JAXBException e) {
            System.err.println("Failed to parse actions.xml: " + actionsPath);
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    /**
     * XmlActionから具体的なActionインスタンスを生成するヘルパーメソッド。
     * "Type"属性に基づいて適切なActionクラスをインスタンス化します。
     */
    private Action createAction(XmlAction xmlAction) {
        // Note: この時点では、Mascotインスタンスに依存する初期化は行えません。
        // Actionの実行時に遅延初期化する必要があります。
        switch (xmlAction.getType()) {
            case "Animate":
                if (xmlAction.getAnimation() == null) {
                    System.err.println("Animate action requires <Animation> tag: " + xmlAction.getName());
                    return null; // or a NoOpAction
                }
                return new AnimateAction(xmlAction.getAnimation());
            case "Move": {
                if (xmlAction.getPoint() == null) {
                    System.err.println("Move action requires <Point> tag: " + xmlAction.getName());
                    return null; // or a NoOpAction
                }
                // アニメーションが同時に定義されていれば、その長さを移動時間として利用します。
                int duration = (xmlAction.getAnimation() != null)
                        ? xmlAction.getAnimation().getPoses().stream().mapToInt(XmlPose::getDuration).sum()
                        : 0; // アニメーションがない場合は即時移動
                return new MoveAction(xmlAction.getPoint(), duration);
            }
            case "Sequence": {
                // SequenceActionは参照を後で解決するため、ここでは空のインスタンスを生成します。
                SequenceAction sequenceAction = new SequenceAction();
                if (xmlAction.getLoop() != null) {
                    sequenceAction.setLoopCount(xmlAction.getLoop());
                }
                return sequenceAction;
            }
            case "RandomChoice": {
                return new RandomChoiceAction();
            }
            case "Turn":
                // マスコットの向きを反転させるアクションを生成します。
                return new TurnAction();
            case "Fall":
                // 落下アクションを生成します。
                return new FallAction(xmlAction.getAnimation());
            case "Dragged": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("Dragged action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                return new DraggedAction(xmlAction.getAnimation());
            }
            case "Jump": {
                int vx = xmlAction.getVelocityX() != null ? xmlAction.getVelocityX() : 0;
                int vy = xmlAction.getVelocityY() != null ? xmlAction.getVelocityY() : 0;
                return new JumpAction(xmlAction.getAnimation(), vy, vx);
            }
            case "Stay": {
                // 指定時間だけ待機するアクションを生成します。
                int duration = xmlAction.getDuration() != null ? xmlAction.getDuration() : 1000;
                return new StayAction(xmlAction.getAnimation(), duration);
            }
            case "LieDown": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("LieDown action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                return new LieDownAction(xmlAction.getAnimation(), xmlAction.getDuration() != null ? xmlAction.getDuration() : 4000);
            }
            case "Breed": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("Breed action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 2000;
                // Pointタグがあれば生成位置のオフセットとして使用 (デフォルトは真上 -100)
                int bornX = (xmlAction.getPoint() != null) ? xmlAction.getPoint().getX() : 0;
                int bornY = (xmlAction.getPoint() != null) ? xmlAction.getPoint().getY() : -100;
                // Velocity属性があれば生成時の初速として使用
                int bornVX = (xmlAction.getVelocityX() != null) ? xmlAction.getVelocityX() : 0;
                int bornVY = (xmlAction.getVelocityY() != null) ? xmlAction.getVelocityY() : 0;
                return new BreedAction(xmlAction.getAnimation(), duration, bornX, bornY, bornVX, bornVY);
            }
            case "Dig": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("Dig action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 2000;
                return new DigAction(xmlAction.getAnimation(), duration);
            }
            case "Gather": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("Gather action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 2;
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 4000;
                return new GatherAction(xmlAction.getAnimation(), speed, duration);
            }
            case "WallCling": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("WallCling action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 1000;
                return new WallClingAction(xmlAction.getAnimation(), duration);
            }
            case "Climb": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("Climb action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 2;
                return new ClimbAction(xmlAction.getAnimation(), speed);
            }
            case "CeilingCrawl": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("CeilingCrawl action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 2;
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 5000;
                return new CeilingCrawlAction(xmlAction.getAnimation(), speed, duration);
            }
            case "SlideDown": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("SlideDown action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 4;
                return new SlideDownAction(xmlAction.getAnimation(), speed);
            }
            case "WallJump": {
                if (xmlAction.getAnimation() == null) {
                    System.err.println("WallJump action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int vx = xmlAction.getVelocityX() != null ? xmlAction.getVelocityX() : 5;
                int vy = xmlAction.getVelocityY() != null ? xmlAction.getVelocityY() : 20;
                return new WallJumpAction(xmlAction.getAnimation(), vy, vx);
            }
            case "Walk":
                if (xmlAction.getAnimation() == null) {
                    System.err.println("Walk action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                // Speed属性がなければデフォルト値(e.g., 1)を使う
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 1;
                return new WalkAction(xmlAction.getAnimation(), speed);
            case "Chase":
                if (xmlAction.getAnimation() == null) {
                    System.err.println("Chase action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int chaseSpeed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 4;
                int chaseDuration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 5000;
                return new ChaseAction(xmlAction.getAnimation(), chaseSpeed, chaseDuration);
            default:
                System.err.println("Unknown action type: " + xmlAction.getType());
                return null; // 不明な型はnullを返す
        }
    }

    /**
     * SequenceActionが参照するアクションを解決して設定します。
     */
    private void resolveSequenceAction(SequenceAction sequenceAction, XmlAction xmlAction, Map<String, Action> builtActions) {
        List<Action> sequence = new ArrayList<>();
        for (XmlActionReference ref : xmlAction.getActionReferences()) {
            Action referencedAction = builtActions.get(ref.getName());
            if (referencedAction != null) {
                sequence.add(referencedAction);
            } else {
                System.err.println("ActionReference not found: " + ref.getName() + " in Sequence " + xmlAction.getName());
            }
        }
        sequenceAction.setSequence(sequence);
    }

    private void resolveRandomChoiceAction(RandomChoiceAction randomAction, XmlAction xmlAction, Map<String, Action> builtActions) {
        List<Action> candidates = new ArrayList<>();
        for (XmlActionReference ref : xmlAction.getActionReferences()) {
            Action referencedAction = builtActions.get(ref.getName());
            if (referencedAction != null) {
                candidates.add(referencedAction);
            } else {
                System.err.println("ActionReference not found: " + ref.getName() + " in RandomChoice " + xmlAction.getName());
            }
        }
        randomAction.setCandidates(candidates);
    }
}