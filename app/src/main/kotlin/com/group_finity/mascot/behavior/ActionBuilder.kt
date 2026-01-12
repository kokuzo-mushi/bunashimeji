package com.group_finity.mascot.behavior

import com.group_finity.mascot.action.*
import com.group_finity.mascot.animation.Animation
import com.group_finity.mascot.animation.Pose
import com.group_finity.mascot.config.ActionConfig
import com.group_finity.mascot.config.XmlSecurity
import com.group_finity.mascot.config.xml.*
import jakarta.xml.bind.JAXBContext
import java.awt.Point
import java.nio.file.Path
import java.util.Collections

class ActionBuilder {

    // === XML Build Logic ===
    fun build(actionsPath: Path): Map<String, Action> {
        println("[DEBUG] ActionBuilder: Loading XML from ${actionsPath.toAbsolutePath()}")
        return try {
            val context = JAXBContext.newInstance(XmlActions::class.java)
            val unmarshaller = context.createUnmarshaller()

            val dbf = XmlSecurity.createSecureFactory()
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(actionsPath.toFile())
            val xmlActions = unmarshaller.unmarshal(doc) as XmlActions

            if (xmlActions.actions == null) {
                return emptyMap()
            }

            val builtActions = HashMap<String, Action>()
            for (xmlAction in xmlActions.actions) {
                val action = createAction(xmlAction)
                if (action != null) {
                    builtActions[xmlAction.name] = action
                }
            }

            for (xmlAction in xmlActions.actions) {
                val action = builtActions[xmlAction.name]
                if (action is SequenceAction) {
                    resolveSequenceAction(action, xmlAction, builtActions)
                } else if (action is RandomChoiceAction) {
                    resolveRandomChoiceAction(action, xmlAction, builtActions)
                } else if (action is ThrowAction) {
                    resolveThrowAction(action, xmlAction, builtActions)
                }
            }

            println("[DEBUG] ActionBuilder: Successfully loaded ${builtActions.size} actions.")
            Collections.unmodifiableMap(builtActions)
        } catch (e: Exception) {
            System.err.println("[ERROR] ActionBuilder: Failed to parse actions.xml: ${actionsPath.toAbsolutePath()}")
            e.printStackTrace()
            emptyMap()
        }
    }

    private fun createAction(xmlAction: XmlAction): Action? {
        return when (xmlAction.type) {
            "Animate" -> {
                val anim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                if (anim == null) {
                    System.err.println("Animate action requires <Animation> tag: " + xmlAction.name)
                    null
                } else AnimateAction(anim)
            }
            "Move" -> {
                if (xmlAction.point == null) {
                    System.err.println("Move action requires <Point> tag: " + xmlAction.name)
                    null
                } else {
                    val duration =
                            if (xmlAction.animation != null)
                                    xmlAction.animation.poses.sumOf { it.duration }
                            else 0
                    MoveAction(Point(xmlAction.point.x, xmlAction.point.y), duration)
                }
            }
            "Sequence" -> {
                val sequenceAction = SequenceAction()
                if (xmlAction.loop != null) {
                    sequenceAction.setLoopCount(xmlAction.loop)
                }
                sequenceAction
            }
            "RandomChoice" -> RandomChoiceAction()
            "Turn" -> TurnAction()
            "Look" -> {
                val dir = xmlAction.velocityX ?: 0
                LookAction(dir >= 0)
            }
            "Fall" -> {
                val fallAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                FallAction(fallAnim)
            }
            "Dragged" -> {
                val xmlAnim = xmlAction.animation
                if (xmlAnim == null || xmlAnim.poses == null) {
                    System.err.println("Dragged action requires <Animation> tag: " + xmlAction.name)
                    null
                } else {
                    val poseAnims = ArrayList<Animation>()
                    var index = 1
                    for (xmlPose in xmlAnim.poses) {
                        var imageName = xmlPose.image
                        if (imageName.isNullOrEmpty()) {
                            imageName = "${xmlAction.name}$index.png"
                        }
                        poseAnims.add(
                                Animation(
                                        listOf(
                                                Pose(
                                                        imageName,
                                                        xmlPose.duration,
                                                        xmlPose.imageAnchorPoint
                                                )
                                        )
                                )
                        )
                        index++
                    }
                    DraggedAction(poseAnims)
                }
            }
            "Jump" -> {
                val vx = xmlAction.velocityX ?: 0
                val vy = xmlAction.velocityY ?: 0
                val jumpAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                JumpAction(jumpAnim, vy, vx)
            }
            "Stay" -> {
                val duration = xmlAction.duration ?: 1000
                val stayAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                StayAction(stayAnim, duration)
            }
            "LieDown" -> {
                val lieDownAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val duration = xmlAction.duration ?: 4000
                LieDownAction(lieDownAnim, duration)
            }
            "Breed" -> {
                if (xmlAction.animation == null) {
                    System.err.println("Breed action requires <Animation> tag: " + xmlAction.name)
                    null
                } else {
                    val duration = xmlAction.duration ?: 2000
                    val bornX = xmlAction.point?.x ?: 0
                    val bornY = xmlAction.point?.y ?: -100
                    val bornVX = xmlAction.velocityX ?: 0
                    val bornVY = xmlAction.velocityY ?: 0
                    val breedAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                    BreedAction(breedAnim, duration, bornX, bornY, bornVX, bornVY)
                }
            }
            "Dig" -> {
                val digAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val duration = xmlAction.duration ?: 2000
                DigAction(digAnim, duration)
            }
            "Gather" -> {
                val gatherAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val speed = xmlAction.speed ?: 2
                val duration = xmlAction.duration ?: 4000
                GatherAction(gatherAnim, speed, duration)
            }
            "WallCling" -> {
                val wallClingAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val duration = xmlAction.duration ?: 1000
                WallClingAction(wallClingAnim, duration)
            }
            "Climb" -> {
                val climbAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val speed = xmlAction.speed ?: 2
                val duration = xmlAction.duration ?: 0
                ClimbAction(climbAnim, speed, duration)
            }
            "ClimbCeiling" -> {
                val climbCeilingAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val speed = xmlAction.speed ?: 2
                val duration = xmlAction.duration ?: 1000
                ClimbCeilingAction(climbCeilingAnim, speed, duration)
            }
            "CeilingEnter" -> {
                val ceilingEnterAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val duration = xmlAction.duration ?: 500
                CeilingEnterAction(ceilingEnterAnim, duration)
            }
            "CornerTurn" -> {
                val cornerTurnAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val duration = xmlAction.duration ?: 1000
                CornerTurnAction(cornerTurnAnim, duration)
            }
            "CornerTurnDown" -> {
                val cornerTurnDownAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val duration = xmlAction.duration ?: 1000
                CornerTurnDownAction(cornerTurnDownAnim, duration)
            }
            "WallTopCling" -> {
                val wallTopClingAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val duration = xmlAction.duration ?: 2000
                WallTopClingAction(wallTopClingAnim, duration)
            }
            "CeilingCrawl" -> {
                val ceilingCrawlAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val speed = xmlAction.speed ?: 2
                val duration = xmlAction.duration ?: 5000
                CeilingCrawlAction(ceilingCrawlAnim, speed, duration)
            }
            "SlideDown" -> {
                val slideDownAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val speed = xmlAction.speed ?: 4
                SlideDownAction(slideDownAnim, speed)
            }
            "WallJump" -> {
                val wallJumpAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                val vx = xmlAction.velocityX ?: 5
                val vy = xmlAction.velocityY ?: 20
                WallJumpAction(wallJumpAnim, vy, vx)
            }
            "Walk" -> {
                val walkAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                if (walkAnim == null) {
                    System.err.println("Walk action requires <Animation> tag: " + xmlAction.name)
                    null
                } else {
                    val speed = xmlAction.speed ?: 1
                    WalkAction(walkAnim, speed)
                }
            }
            "Chase" -> {
                val chaseAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                if (chaseAnim == null) {
                    System.err.println("Chase action requires <Animation> tag: " + xmlAction.name)
                    null
                } else {
                    val speed = xmlAction.speed ?: 4
                    val duration = xmlAction.duration ?: 5000
                    ChaseAction(chaseAnim, speed, duration)
                }
            }
            "Grab" -> {
                val grabAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                if (grabAnim == null) {
                    System.err.println("Grab action requires <Animation> tag: " + xmlAction.name)
                    null
                } else GrabAction(grabAnim)
            }
            "Throw" -> {
                val throwAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                if (throwAnim == null) {
                    System.err.println("Throw action requires <Animation> tag: " + xmlAction.name)
                    null
                } else ThrowAction(throwAnim)
            }
            "Teeter" -> {
                val teeterAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                if (teeterAnim == null) {
                    System.err.println("Teeter action requires <Animation> tag: " + xmlAction.name)
                    null
                } else {
                    val duration = xmlAction.duration ?: 4000
                    val fallProbability = xmlAction.fallProbability ?: 0.2
                    TeeterAction(teeterAnim, duration, fallProbability)
                }
            }
            "PullUp" -> {
                val pullUpAnim = createAnimationFromXml(xmlAction.animation, xmlAction.name)
                if (pullUpAnim == null) {
                    System.err.println("PullUp action requires <Animation> tag: " + xmlAction.name)
                    null
                } else {
                    val duration = xmlAction.duration ?: 1000
                    PullUpAction(pullUpAnim, duration)
                }
            }
            else -> {
                System.err.println("Unknown action type: " + xmlAction.type)
                null
            }
        }
    }

    private fun resolveSequenceAction(
            sequenceAction: SequenceAction,
            xmlAction: XmlAction,
            builtActions: Map<String, Action>
    ) {
        val sequence = ArrayList<Action>()
        for (ref in xmlAction.actionReferences) {
            val referencedAction = builtActions[ref.name]
            if (referencedAction != null) {
                sequence.add(referencedAction)
            } else {
                System.err.println(
                        "ActionReference not found: ${ref.name} in Sequence ${xmlAction.name}"
                )
            }
        }
        sequenceAction.setSequence(sequence)
    }

    private fun resolveRandomChoiceAction(
            randomAction: RandomChoiceAction,
            xmlAction: XmlAction,
            builtActions: Map<String, Action>
    ) {
        val candidates = ArrayList<Action>()
        for (ref in xmlAction.actionReferences) {
            val referencedAction = builtActions[ref.name]
            if (referencedAction != null) {
                candidates.add(referencedAction)
            } else {
                System.err.println(
                        "ActionReference not found: ${ref.name} in RandomChoice ${xmlAction.name}"
                )
            }
        }
        randomAction.setCandidates(candidates)
    }

    private fun resolveThrowAction(
            throwAction: ThrowAction,
            xmlAction: XmlAction,
            builtActions: Map<String, Action>
    ) {
        if (!xmlAction.actionReferences.isNullOrEmpty()) {
            val refName = xmlAction.actionReferences[0].name
            val referencedAction = builtActions[refName]

            if (referencedAction != null) {
                when (referencedAction) {
                    is StayAction -> throwAction.setCelebrationAnimation(referencedAction.animation)
                    is AnimateAction ->
                            throwAction.setCelebrationAnimation(referencedAction.animation)
                }
            } else {
                System.err.println("ActionReference not found: $refName in Throw ${xmlAction.name}")
            }
        }
    }

    private fun createAnimationFromXml(
            xmlAnimation: XmlAnimation?,
            actionName: String?
    ): Animation? {
        if (xmlAnimation?.poses == null) {
            return null
        }
        val poses = ArrayList<Pose>()
        var index = 1
        for (xmlPose in xmlAnimation.poses) {
            var imageName = xmlPose.image
            if (imageName.isNullOrEmpty()) {
                imageName = "${actionName ?: ""}$index.png"
            }
            // XmlPoint -> java.awt.Point 変換
            val anchor = xmlPose.imageAnchorPoint?.let { Point(it.x, it.y) }
            poses.add(Pose(imageName, xmlPose.duration, anchor))
            index++
        }
        return Animation(poses)
    }

    // === YAML Build Logic ===
    fun build(actionConfigs: List<ActionConfig>?): Map<String, Action> {
        if (actionConfigs == null) return emptyMap()

        val builtActions = HashMap<String, Action>()
        for (config in actionConfigs) {
            val action = createActionFromConfig(config)
            if (action != null) {
                builtActions[config.name] = action
            }
        }

        for (config in actionConfigs) {
            val action = builtActions[config.name]
            if (action is SequenceAction) {
                resolveSequenceActionConfig(action, config, builtActions)
            } else if (action is RandomChoiceAction) {
                resolveRandomChoiceActionConfig(action, config, builtActions)
            } else if (action is ThrowAction) {
                resolveThrowActionConfig(action, config, builtActions)
            }
        }
        return Collections.unmodifiableMap(builtActions)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createActionFromConfig(config: ActionConfig): Action? {
        val params = config.params

        return when (config.type) {
            "Animate" -> {
                val anim = createAnimationFromConfig(params, config.name)
                if (anim == null) {
                    System.err.println("Animate action requires Animation param: " + config.name)
                    null
                } else AnimateAction(anim)
            }
            "Move" -> {
                if (!params.containsKey("Point")) {
                    System.err.println("Move action requires Point param: " + config.name)
                    null
                } else {
                    val pointMap = params["Point"] as Map<String, Int>
                    val point = Point(pointMap["x"] ?: 0, pointMap["y"] ?: 0)
                    var duration = 0
                    if (params.containsKey("Animation")) {
                        val a = createAnimationFromConfig(params, config.name)
                        if (a != null) {
                            duration = a.totalDuration
                        }
                    }
                    MoveAction(point, duration)
                }
            }
            "Sequence" -> {
                val sa = SequenceAction()
                if (params.containsKey("Loop")) {
                    sa.setLoopCount((params["Loop"] as Number).toInt())
                }
                sa
            }
            "RandomChoice" -> RandomChoiceAction()
            "Turn" -> TurnAction()
            "Look" -> {
                var dir = 0
                if (params.containsKey("VelocityX")) {
                    dir = (params["VelocityX"] as Number).toInt()
                }
                LookAction(dir >= 0)
            }
            "Fall" -> {
                val fallAnim = createAnimationFromConfig(params, config.name)
                FallAction(fallAnim)
            }
            "Dragged" -> createDraggedActionFromConfig(params, config.name)
            "Jump" -> {
                val jumpAnim = createAnimationFromConfig(params, config.name)
                val jvx =
                        if (params.containsKey("VelocityX")) (params["VelocityX"] as Number).toInt()
                        else 0
                val jvy =
                        if (params.containsKey("VelocityY")) (params["VelocityY"] as Number).toInt()
                        else 0
                JumpAction(jumpAnim, jvy, jvx)
            }
            "Stay" -> {
                val stayAnim = createAnimationFromConfig(params, config.name)
                val sDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 1000
                StayAction(stayAnim, sDuration)
            }
            "LieDown" -> {
                val lieAnim = createAnimationFromConfig(params, config.name)
                val lDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 4000
                LieDownAction(lieAnim, lDuration)
            }
            "Breed" -> {
                if (!params.containsKey("Animation")) {
                    System.err.println("Breed action requires Animation param: " + config.name)
                    null
                } else {
                    val bDuration =
                            if (params.containsKey("Duration"))
                                    (params["Duration"] as Number).toInt()
                            else 2000
                    var bX = 0
                    var bY = -100
                    if (params.containsKey("Point")) {
                        val p = params["Point"] as Map<String, Int>
                        bX = p["x"] ?: 0
                        bY = p["y"] ?: -100
                    }
                    val bVX =
                            if (params.containsKey("VelocityX"))
                                    (params["VelocityX"] as Number).toInt()
                            else 0
                    val bVY =
                            if (params.containsKey("VelocityY"))
                                    (params["VelocityY"] as Number).toInt()
                            else 0
                    val breedAnim = createAnimationFromConfig(params, config.name)
                    BreedAction(breedAnim, bDuration, bX, bY, bVX, bVY)
                }
            }
            "Dig" -> {
                val digAnim = createAnimationFromConfig(params, config.name)
                val dDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 2000
                DigAction(digAnim, dDuration)
            }
            "Gather" -> {
                val gatherAnim = createAnimationFromConfig(params, config.name)
                val gSpeed =
                        if (params.containsKey("Speed")) (params["Speed"] as Number).toInt() else 2
                val gDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 4000
                GatherAction(gatherAnim, gSpeed, gDuration)
            }
            "WallCling" -> {
                val wcAnim = createAnimationFromConfig(params, config.name)
                val wcDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 1000
                WallClingAction(wcAnim, wcDuration)
            }
            "Climb" -> {
                val cAnim = createAnimationFromConfig(params, config.name)
                val cSpeed =
                        if (params.containsKey("Speed")) (params["Speed"] as Number).toInt() else 2
                val cDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 0
                ClimbAction(cAnim, cSpeed, cDuration)
            }
            "ClimbCeiling" -> {
                val ccAnim = createAnimationFromConfig(params, config.name)
                val ccSpeed =
                        if (params.containsKey("Speed")) (params["Speed"] as Number).toInt() else 2
                val ccDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 1000
                ClimbCeilingAction(ccAnim, ccSpeed, ccDuration)
            }
            "CeilingEnter" -> {
                val ceAnim = createAnimationFromConfig(params, config.name)
                val ceDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 500
                CeilingEnterAction(ceAnim, ceDuration)
            }
            "CornerTurn" -> {
                val ctAnim = createAnimationFromConfig(params, config.name)
                val ctDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 1000
                CornerTurnAction(ctAnim, ctDuration)
            }
            "CornerTurnDown" -> {
                val ctdAnim = createAnimationFromConfig(params, config.name)
                val ctdDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 1000
                CornerTurnDownAction(ctdAnim, ctdDuration)
            }
            "WallTopCling" -> {
                val wtcAnim = createAnimationFromConfig(params, config.name)
                val wtcDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 2000
                WallTopClingAction(wtcAnim, wtcDuration)
            }
            "CeilingCrawl" -> {
                val ccrAnim = createAnimationFromConfig(params, config.name)
                val ccrSpeed =
                        if (params.containsKey("Speed")) (params["Speed"] as Number).toInt() else 2
                val ccrDuration =
                        if (params.containsKey("Duration")) (params["Duration"] as Number).toInt()
                        else 5000
                CeilingCrawlAction(ccrAnim, ccrSpeed, ccrDuration)
            }
            "SlideDown" -> {
                val sdAnim = createAnimationFromConfig(params, config.name)
                val sdSpeed =
                        if (params.containsKey("Speed")) (params["Speed"] as Number).toInt() else 4
                SlideDownAction(sdAnim, sdSpeed)
            }
            "WallJump" -> {
                val wjAnim = createAnimationFromConfig(params, config.name)
                val wjVx =
                        if (params.containsKey("VelocityX")) (params["VelocityX"] as Number).toInt()
                        else 5
                val wjVy =
                        if (params.containsKey("VelocityY")) (params["VelocityY"] as Number).toInt()
                        else 20
                WallJumpAction(wjAnim, wjVy, wjVx)
            }
            "Walk" -> {
                val wkAnim = createAnimationFromConfig(params, config.name)
                if (wkAnim == null) {
                    System.err.println("Walk action requires Animation param: " + config.name)
                    null
                } else {
                    val wkSpeed =
                            if (params.containsKey("Speed")) (params["Speed"] as Number).toInt()
                            else 1
                    WalkAction(wkAnim, wkSpeed)
                }
            }
            "Chase" -> {
                val chAnim = createAnimationFromConfig(params, config.name)
                if (chAnim == null) {
                    System.err.println("Chase action requires Animation param: " + config.name)
                    null
                } else {
                    val chSpeed =
                            if (params.containsKey("Speed")) (params["Speed"] as Number).toInt()
                            else 4
                    val chDuration =
                            if (params.containsKey("Duration"))
                                    (params["Duration"] as Number).toInt()
                            else 5000
                    ChaseAction(chAnim, chSpeed, chDuration)
                }
            }
            "Grab" -> {
                val gbAnim = createAnimationFromConfig(params, config.name)
                if (gbAnim == null) {
                    System.err.println("Grab action requires Animation param: " + config.name)
                    null
                } else GrabAction(gbAnim)
            }
            "Throw" -> {
                val thAnim = createAnimationFromConfig(params, config.name)
                if (thAnim == null) {
                    System.err.println("Throw action requires Animation param: " + config.name)
                    null
                } else ThrowAction(thAnim)
            }
            "Teeter" -> {
                val teAnim = createAnimationFromConfig(params, config.name)
                if (teAnim == null) {
                    System.err.println("Teeter action requires Animation param: " + config.name)
                    null
                } else {
                    val teDuration =
                            if (params.containsKey("Duration"))
                                    (params["Duration"] as Number).toInt()
                            else 4000
                    val teProb =
                            if (params.containsKey("FallProbability"))
                                    (params["FallProbability"] as Number).toDouble()
                            else 0.2
                    TeeterAction(teAnim, teDuration, teProb)
                }
            }
            "PullUp" -> {
                val puAnim = createAnimationFromConfig(params, config.name)
                if (puAnim == null) {
                    System.err.println("PullUp action requires Animation param: " + config.name)
                    null
                } else {
                    val puDuration =
                            if (params.containsKey("Duration"))
                                    (params["Duration"] as Number).toInt()
                            else 1000
                    PullUpAction(puAnim, puDuration)
                }
            }
            "Script" -> { // Added Script Action support logic implicitly or todo?
                System.err.println(
                        "Script action not fully supported in builder yet: " + config.name
                ) // or similar
                null
            }
            else -> {
                System.err.println("Unknown action type: " + config.type)
                null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createAnimationFromConfig(
            params: Map<String, Any>,
            actionName: String
    ): Animation? {
        if (!params.containsKey("Animation")) return null

        val posesData = params["Animation"] as? List<Map<String, Any>> ?: return null

        val poses = ArrayList<Pose>()
        var index = 1
        for (poseData in posesData) {
            var imageName = poseData["Image"] as? String
            if (imageName.isNullOrEmpty()) {
                imageName = "$actionName$index.png"
            }
            val duration =
                    if (poseData.containsKey("Duration")) (poseData["Duration"] as Number).toInt()
                    else 0

            var anchor: Point? = null
            if (poseData.containsKey("ImageAnchor")) {
                val p = poseData["ImageAnchor"] as Map<String, Int>
                anchor = Point(p["x"] ?: 0, p["y"] ?: 0)
            }

            poses.add(Pose(imageName, duration, anchor))
            index++
        }
        return Animation(poses)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createDraggedActionFromConfig(
            params: Map<String, Any>,
            actionName: String
    ): Action? {
        if (!params.containsKey("Animation")) {
            System.err.println("Dragged action requires Animation param: $actionName")
            return null
        }

        val posesData = params["Animation"] as? List<Map<String, Any>> ?: return null

        val poseAnims = ArrayList<Animation>()
        var index = 1
        for (poseData in posesData) {
            var imageName = poseData["Image"] as? String
            if (imageName.isNullOrEmpty()) {
                imageName = "$actionName$index.png"
            }
            val duration =
                    if (poseData.containsKey("Duration")) (poseData["Duration"] as Number).toInt()
                    else 0

            var anchor: Point? = null
            if (poseData.containsKey("ImageAnchor")) {
                val p = poseData["ImageAnchor"] as Map<String, Int>
                anchor = Point(p["x"] ?: 0, p["y"] ?: 0)
            }

            poseAnims.add(Animation(listOf(Pose(imageName, duration, anchor))))
            index++
        }
        return DraggedAction(poseAnims)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveSequenceActionConfig(
            sequenceAction: SequenceAction,
            config: ActionConfig,
            builtActions: Map<String, Action>
    ) {
        if (!config.params.containsKey("ActionReferences")) return

        val refs = config.params["ActionReferences"] as? List<Map<String, String>> ?: return
        val sequence = ArrayList<Action>()

        for (ref in refs) {
            val name = ref["Name"]
            val a = builtActions[name]
            if (a != null) {
                sequence.add(a)
            } else {
                System.err.println("ActionReference not found: $name in Sequence ${config.name}")
            }
        }
        sequenceAction.setSequence(sequence)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveRandomChoiceActionConfig(
            randomAction: RandomChoiceAction,
            config: ActionConfig,
            builtActions: Map<String, Action>
    ) {
        if (!config.params.containsKey("ActionReferences")) return

        val refs = config.params["ActionReferences"] as? List<Map<String, String>> ?: return
        val candidates = ArrayList<Action>()

        for (ref in refs) {
            val name = ref["Name"]
            val a = builtActions[name]
            if (a != null) {
                candidates.add(a)
            } else {
                System.err.println(
                        "ActionReference not found: $name in RandomChoice ${config.name}"
                )
            }
        }
        randomAction.setCandidates(candidates)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveThrowActionConfig(
            throwAction: ThrowAction,
            config: ActionConfig,
            builtActions: Map<String, Action>
    ) {
        if (!config.params.containsKey("ActionReferences")) return
        val refs = config.params["ActionReferences"] as? List<Map<String, String>>
        if (refs.isNullOrEmpty()) return

        val refName = refs[0]["Name"]
        val referencedAction = builtActions[refName]

        if (referencedAction != null) {
            when (referencedAction) {
                is StayAction -> throwAction.setCelebrationAnimation(referencedAction.animation)
                is AnimateAction -> throwAction.setCelebrationAnimation(referencedAction.animation)
            }
        } else {
            System.err.println("ActionReference not found: $refName in Throw ${config.name}")
        }
    }
}
