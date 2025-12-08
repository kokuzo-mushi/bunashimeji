package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.action.AnimateAction;
import com.group_finity.mascot.action.MoveAction;
import com.group_finity.mascot.action.SequenceAction;
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
            case "Move":
                if (xmlAction.getPoint() == null) {
                    System.err.println("Move action requires <Point> tag: " + xmlAction.getName());
                    return null; // or a NoOpAction
                }
                // アニメーションが同時に定義されていれば、その長さを移動時間として利用します。
                int duration = (xmlAction.getAnimation() != null)
                        ? xmlAction.getAnimation().getPoses().stream().mapToInt(XmlPose::getDuration).sum()
                        : 0; // アニメーションがない場合は即時移動
                return new MoveAction(xmlAction.getPoint(), duration);
            case "Sequence":
                // SequenceActionは参照を後で解決するため、ここでは空のインスタンスを生成します。
                return new SequenceAction();
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
}