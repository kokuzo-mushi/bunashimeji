package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.action.SequenceAction;
import com.group_finity.mascot.config.xml.XmlBehavior;
import com.group_finity.mascot.config.xml.XmlBehaviors;
import com.group_finity.mascot.config.xml.XmlActionReference;
import com.group_finity.mascot.trigger.Trigger;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds a list of {@link Behavior}s from an XML configuration file.
 * <p>
 * It depends on a pre-built map of named actions to link triggers with their resulting actions.
 * <p>
 * Example XML:
 * <pre>{@code
 * <behaviors>
 *   <behavior>
 *     <condition>mascot.isGrounded</condition>
 *     <action ref="Walk" />
 *   </behavior>
 * </behaviors>
 * }</pre>
 */
public class BehaviorBuilder {

    private final Map<String, Action> actions;

    public BehaviorBuilder(Map<String, Action> actions) {
        this.actions = actions;
    }

    public List<Behavior> build(Path behaviorsPath) {
        try {
            JAXBContext context = JAXBContext.newInstance(XmlBehaviors.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            XmlBehaviors xmlBehaviors = (XmlBehaviors) unmarshaller.unmarshal(behaviorsPath.toFile());

            if (xmlBehaviors.getBehaviors() == null) {
                return Collections.emptyList();
            }

            List<Behavior> builtBehaviors = new ArrayList<>();
            for (XmlBehavior xmlBehavior : xmlBehaviors.getBehaviors()) {
                // 実行するActionを解決または生成します。
                Action action = createActionForBehavior(xmlBehavior);

                if (action != null) {
                    // Behaviorクラスを直接生成し、リストに追加します。
                    String name = (xmlBehavior.getName() != null) ? xmlBehavior.getName() : "Behavior";
                    boolean hidden = xmlBehavior.isHidden();
                    int frequency = (xmlBehavior.getFrequency() != null) ? xmlBehavior.getFrequency() : 1;
                    builtBehaviors.add(new Behavior(name, action, xmlBehavior.getCondition(), hidden, frequency));
                } else {
                    System.err.println("No valid action found for condition: " + xmlBehavior.getCondition());
                }
            }
            return Collections.unmodifiableList(builtBehaviors);

        } catch (JAXBException e) {
            System.err.println("Failed to parse behaviors.xml: " + behaviorsPath);
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private Action createActionForBehavior(XmlBehavior xmlBehavior) {
        List<XmlActionReference> references = xmlBehavior.getActionReferences();
        if (references == null || references.isEmpty()) {
            return null;
        }

        if (references.size() == 1) {
            // ActionReferenceが1つの場合は、対応するActionをそのまま返します。
            String actionName = references.get(0).getName();
            Action action = actions.get(actionName);
            if (action == null) {
                System.err.println("Action not found for behavior: " + actionName);
            }
            return action;
        } else {
            // ActionReferenceが複数の場合は、それらを順に実行するSequenceActionを動的に生成します。
            List<Action> sequence = new ArrayList<>();
            for (XmlActionReference ref : references) {
                Action referencedAction = actions.get(ref.getName());
                if (referencedAction != null) {
                    sequence.add(referencedAction);
                } else {
                    System.err.println("ActionReference not found: " + ref.getName() + " in behavior with condition: " + xmlBehavior.getCondition());
                }
            }
            // SequenceActionは別途実装が必要です。
            return sequence.isEmpty() ? null : new SequenceAction(sequence);
        }
    }
}