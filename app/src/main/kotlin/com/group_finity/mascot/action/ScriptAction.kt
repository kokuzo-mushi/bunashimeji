package com.group_finity.mascot.action

import com.group_finity.mascot.Mascot
import com.group_finity.mascot.script.ScriptBehavior

/**
 * ScriptBehavior (JS Generator) を Action インターフェースに適合させるアダプター。
 */
class ScriptAction(private val behavior: ScriptBehavior) : Action {
    override fun execute(mascot: Mascot) {
        behavior.tick()
    }

    override fun hasNext(): Boolean {
        return !behavior.isFinished()
    }
}