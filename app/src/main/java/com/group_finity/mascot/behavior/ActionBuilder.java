package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.action.CeilingEnterAction;
import com.group_finity.mascot.action.ClimbCeilingAction;
import com.group_finity.mascot.action.AnimateAction;
import com.group_finity.mascot.action.CornerTurnAction;
import com.group_finity.mascot.action.CornerTurnDownAction;
import com.group_finity.mascot.action.WallTopClingAction;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.animation.Pose;
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
import com.group_finity.mascot.action.GrabAction;
import com.group_finity.mascot.action.ThrowAction;
import com.group_finity.mascot.action.TeeterAction;
import com.group_finity.mascot.action.PullUpAction;
import com.group_finity.mascot.action.LookAction;
import com.group_finity.mascot.config.xml.XmlPose;
import com.group_finity.mascot.config.xml.XmlAction;
import com.group_finity.mascot.config.xml.XmlActionReference;
import com.group_finity.mascot.config.xml.XmlAnimation;
import com.group_finity.mascot.config.xml.XmlActions;
import com.group_finity.mascot.config.XmlSecurity;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

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

            // XXE対策: Secure DocumentBuilderFactoryを使用
            DocumentBuilderFactory dbf = XmlSecurity.createSecureFactory();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(actionsPath.toFile());
            XmlActions xmlActions = (XmlActions) unmarshaller.unmarshal(doc);

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
                } else if (action instanceof ThrowAction) {
                    resolveThrowAction((ThrowAction) action, xmlAction, builtActions);
                }
            }

            return Collections.unmodifiableMap(builtActions);

        } catch (Exception e) {
            System.err.println("Failed to parse actions.xml: " + actionsPath);
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private Animation createAnimationFromXml(XmlAnimation xmlAnimation, String actionName) {
        if (xmlAnimation == null || xmlAnimation.getPoses() == null) {
            return null;
        }
        List<Pose> poses = new ArrayList<>();
        int index = 1;
        for (XmlPose xmlPose : xmlAnimation.getPoses()) {
            String imageName = xmlPose.getImage();
            if (imageName == null || imageName.isEmpty()) {
                imageName = actionName + index + ".png";
            }
            poses.add(new Pose(imageName, xmlPose.getDuration(), xmlPose.getImageAnchorPoint()));
            index++;
        }
        return new Animation(poses);
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
                Animation anim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                if (anim == null) {
                    System.err.println("Animate action requires <Animation> tag: " + xmlAction.getName());
                    return null; // or a NoOpAction
                }
                return new AnimateAction(anim);
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
            case "Look":
                // マスコットの向きを指定します。VelocityX > 0 なら右、< 0 なら左とみなします。
                int dir = xmlAction.getVelocityX() != null ? xmlAction.getVelocityX() : 0;
                boolean lookRight = dir >= 0;
                return new LookAction(lookRight);
            case "Fall":
                // 落下アクションを生成します。
                Animation fallAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                return new FallAction(fallAnim);
            case "Dragged": {
                XmlAnimation xmlAnim = xmlAction.getAnimation();
                if (xmlAnim == null || xmlAnim.getPoses() == null) {
                    System.err.println("Dragged action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                List<Animation> poseAnims = new ArrayList<>();
                int index = 1;
                for (XmlPose xmlPose : xmlAnim.getPoses()) {
                    String imageName = xmlPose.getImage();
                    if (imageName == null || imageName.isEmpty()) {
                        imageName = xmlAction.getName() + index + ".png";
                    }
                    poseAnims.add(new Animation(List.of(new Pose(imageName, xmlPose.getDuration(), xmlPose.getImageAnchorPoint()))));
                    index++;
                }
                return new DraggedAction(poseAnims);
            }
            case "Jump": {
                int vx = xmlAction.getVelocityX() != null ? xmlAction.getVelocityX() : 0;
                int vy = xmlAction.getVelocityY() != null ? xmlAction.getVelocityY() : 0;
                Animation jumpAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                return new JumpAction(jumpAnim, vy, vx);
            }
            case "Stay": {
                // 指定時間だけ待機するアクションを生成します。
                int duration = xmlAction.getDuration() != null ? xmlAction.getDuration() : 1000;
                Animation stayAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                return new StayAction(stayAnim, duration);
            }
            case "LieDown": {
                Animation lieDownAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                return new LieDownAction(lieDownAnim, xmlAction.getDuration() != null ? xmlAction.getDuration() : 4000);
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
                Animation breedAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                return new BreedAction(breedAnim, duration, bornX, bornY, bornVX, bornVY);
            }
            case "Dig": {
                Animation digAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 2000;
                return new DigAction(digAnim, duration);
            }
            case "Gather": {
                Animation gatherAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 2;
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 4000;
                return new GatherAction(gatherAnim, speed, duration);
            }
            case "WallCling": {
                Animation wallClingAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 1000;
                return new WallClingAction(wallClingAnim, duration);
            }
            case "Climb": {
                Animation climbAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 2;
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 0;
                return new ClimbAction(climbAnim, speed, duration);
            }
            case "ClimbCeiling": {
                Animation climbCeilingAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 2;
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 1000;
                return new ClimbCeilingAction(climbCeilingAnim, speed, duration);
            }
            case "CeilingEnter": {
                Animation ceilingEnterAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 500;
                return new CeilingEnterAction(ceilingEnterAnim, duration);
            }
            case "CornerTurn": {
                Animation cornerTurnAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 1000;
                return new CornerTurnAction(cornerTurnAnim, duration);
            }
            case "CornerTurnDown": {
                Animation cornerTurnDownAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 1000;
                return new CornerTurnDownAction(cornerTurnDownAnim, duration);
            }
            case "WallTopCling": {
                Animation wallTopClingAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 2000;
                return new WallTopClingAction(wallTopClingAnim, duration);
            }
            case "CeilingCrawl": {
                Animation ceilingCrawlAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 2;
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 5000;
                return new CeilingCrawlAction(ceilingCrawlAnim, speed, duration);
            }
            case "SlideDown": {
                Animation slideDownAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 4;
                return new SlideDownAction(slideDownAnim, speed);
            }
            case "WallJump": {
                Animation wallJumpAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                int vx = xmlAction.getVelocityX() != null ? xmlAction.getVelocityX() : 5;
                int vy = xmlAction.getVelocityY() != null ? xmlAction.getVelocityY() : 20;
                return new WallJumpAction(wallJumpAnim, vy, vx);
            }
            case "Walk":
                Animation walkAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                if (walkAnim == null) {
                    System.err.println("Walk action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                // Speed属性がなければデフォルト値(e.g., 1)を使う
                int speed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 1;
                return new WalkAction(walkAnim, speed);
            case "Chase":
                Animation chaseAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                if (chaseAnim == null) {
                    System.err.println("Chase action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int chaseSpeed = (xmlAction.getSpeed() != null) ? xmlAction.getSpeed() : 4;
                int chaseDuration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 5000;
                return new ChaseAction(chaseAnim, chaseSpeed, chaseDuration);
            case "Grab":
                Animation grabAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                if (grabAnim == null) {
                    System.err.println("Grab action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                return new GrabAction(grabAnim);
            case "Throw":
                Animation throwAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                if (throwAnim == null) {
                    System.err.println("Throw action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                return new ThrowAction(throwAnim);
            case "Teeter": {
                Animation teeterAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                if (teeterAnim == null) {
                    System.err.println("Teeter action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 4000;
                double fallProbability = (xmlAction.getFallProbability() != null) ? xmlAction.getFallProbability() : 0.2;
                return new TeeterAction(teeterAnim, duration, fallProbability);
            }
            case "PullUp": {
                Animation pullUpAnim = createAnimationFromXml(xmlAction.getAnimation(), xmlAction.getName());
                if (pullUpAnim == null) {
                    System.err.println("PullUp action requires <Animation> tag: " + xmlAction.getName());
                    return null;
                }
                int duration = (xmlAction.getDuration() != null) ? xmlAction.getDuration() : 1000;
                return new PullUpAction(pullUpAnim, duration);
            }
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

    private void resolveThrowAction(ThrowAction throwAction, XmlAction xmlAction, Map<String, Action> builtActions) {
        // ThrowActionがActionReferenceを持っている場合、それを勝利ポーズとして使用する
        if (xmlAction.getActionReferences() != null && !xmlAction.getActionReferences().isEmpty()) {
            String refName = xmlAction.getActionReferences().get(0).getName();
            Action referencedAction = builtActions.get(refName);
            
            if (referencedAction != null) {
                if (referencedAction instanceof StayAction) {
                    throwAction.setCelebrationAnimation(((StayAction) referencedAction).getAnimation());
                } else if (referencedAction instanceof AnimateAction) {
                    throwAction.setCelebrationAnimation(((AnimateAction) referencedAction).getAnimation());
                }
            } else {
                System.err.println("ActionReference not found: " + refName + " in Throw " + xmlAction.getName());
            }
        }
    }
}