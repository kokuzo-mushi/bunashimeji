package com.group_finity.mascot.behavior

import com.group_finity.mascot.action.Action
import com.group_finity.mascot.action.SequenceAction
import com.group_finity.mascot.config.BehaviorConfig
import com.group_finity.mascot.config.XmlSecurity
import com.group_finity.mascot.config.xml.XmlBehavior
import com.group_finity.mascot.config.xml.XmlBehaviors
import jakarta.xml.bind.JAXBContext
import java.nio.file.Path
import java.util.Collections

class BehaviorBuilder(private val actions: Map<String, Action>) {

    // === XML Build Logic ===
    fun build(behaviorsPath: Path): List<Behavior> {
        return try {
            val context = JAXBContext.newInstance(XmlBehaviors::class.java)
            val unmarshaller = context.createUnmarshaller()

            val dbf = XmlSecurity.createSecureFactory()
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(behaviorsPath.toFile())
            val xmlBehaviors = unmarshaller.unmarshal(doc) as XmlBehaviors

            if (xmlBehaviors.behaviors == null) {
                return emptyList()
            }

            val builtBehaviors = ArrayList<Behavior>()
            for (xmlBehavior in xmlBehaviors.behaviors) {
                val action = createActionForBehavior(xmlBehavior)

                if (action != null) {
                    val name = xmlBehavior.name ?: "Behavior"
                    val hidden = xmlBehavior.isHidden
                    val frequency = xmlBehavior.frequency ?: 1
                    builtBehaviors.add(
                            Behavior(name, action, xmlBehavior.condition, hidden, frequency)
                    )
                } else {
                    System.err.println(
                            "No valid action found for condition: " + xmlBehavior.condition
                    )
                }
            }
            Collections.unmodifiableList(builtBehaviors)
        } catch (e: Exception) {
            System.err.println("Failed to parse behaviors.xml: $behaviorsPath")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun createActionForBehavior(xmlBehavior: XmlBehavior): Action? {
        val references = xmlBehavior.actionReferences
        if (references.isNullOrEmpty()) {
            return null
        }

        if (references.size == 1) {
            val actionName = references[0].name
            val action = actions[actionName]
            if (action == null) {
                System.err.println("Action not found for behavior: $actionName")
            }
            return action
        } else {
            val sequence = ArrayList<Action>()
            for (ref in references) {
                val referencedAction = actions[ref.name]
                if (referencedAction != null) {
                    sequence.add(referencedAction)
                } else {
                    System.err.println(
                            "ActionReference not found: ${ref.name} in behavior with condition: ${xmlBehavior.condition}"
                    )
                }
            }
            return if (sequence.isEmpty()) null else SequenceAction(sequence)
        }
    }

    // === YAML Build Logic ===
    fun build(behaviorConfigs: List<BehaviorConfig>?): List<Behavior> {
        if (behaviorConfigs == null) return emptyList()

        val builtBehaviors = ArrayList<Behavior>()
        for (config in behaviorConfigs) {
            val action = createActionForBehaviorConfig(config)

            if (action != null) {
                val name = config.name
                val hidden = false // YAML default
                val frequency = config.frequency
                // YAML "conditions" is a List<String>, but behavior expects a single String
                // condition.
                // We join them with "&&" or just take the first one. For now, logic match XML:
                // single string.
                // Taking first or defaulting to true.
                val conditionStr =
                        if (config.conditions.isNotEmpty()) config.conditions[0] else "true"

                builtBehaviors.add(Behavior(name, action, conditionStr, hidden, frequency))
            } else {
                System.err.println("No valid action found for behavior: ${config.name}")
            }
        }
        return Collections.unmodifiableList(builtBehaviors)
    }

    private fun createActionForBehaviorConfig(config: BehaviorConfig): Action? {
        val actionNames = config.actions
        if (actionNames.isEmpty()) {
            return null
        }

        if (actionNames.size == 1) {
            val actionName = actionNames[0]
            val action = actions[actionName]
            if (action == null) {
                System.err.println("Action not found for behavior: $actionName")
            }
            return action
        } else {
            val sequence = ArrayList<Action>()
            for (name in actionNames) {
                val referencedAction = actions[name]
                if (referencedAction != null) {
                    sequence.add(referencedAction)
                } else {
                    System.err.println(
                            "ActionReference not found: $name in behavior: ${config.name}"
                    )
                }
            }
            return if (sequence.isEmpty()) null else SequenceAction(sequence)
        }
    }
}
