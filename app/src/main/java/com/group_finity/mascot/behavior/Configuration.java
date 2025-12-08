package com.group_finity.mascot.behavior;

import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.ActionBuilder;
import com.group_finity.mascot.behavior.BehaviorBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the loading of mascot configurations from XML files.
 * This class uses specialized builders to parse actions and behaviors.
 */
public class Configuration {

    private final Map<String, Action> actions;
    private final List<Behavior> behaviors;

    public Configuration(Path actionsPath, Path behaviorsPath) {
        // The loading process is a two-step dance:
        // 1. First, build all available actions.
        ActionBuilder actionBuilder = new ActionBuilder();
        this.actions = actionBuilder.build(actionsPath);

        // 2. Then, build the behaviors, providing the map of actions to link to.
        BehaviorBuilder behaviorBuilder = new BehaviorBuilder(this.actions);
        this.behaviors = behaviorBuilder.build(behaviorsPath);
    }

    public List<Behavior> getBehaviors() {
        return behaviors;
    }

    public Map<String, Action> getActions() {
        return actions;
    }

    /**
     * A simple example of how this class would be used.
     */
    public static void main(String[] args) {
        // Configuration config = new Configuration(Path.of("conf/actions.xml"), Path.of("conf/behaviors.xml"));
        // List<Behavior> loadedBehaviors = config.getBehaviors();
        // Now, register these behaviors with the EventDispatcher.
    }
}