package com.group_finity.mascot.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.outputStream

/**
 * 既存のXML設定ファイルをYAML形式に変換するユーティリティ。
 */
object XmlToYamlConverter {
    private val yamlMapper = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
        .registerKotlinModule()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    fun convert(xmlPath: Path, yamlPath: Path, type: String) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlPath.toFile())
        doc.documentElement.normalize()

        val config = when (type.lowercase()) {
            "actions" -> {
                val actionNodes = doc.getElementsByTagName("Action")
                val actions = (0 until actionNodes.length).map { i ->
                    parseAction(actionNodes.item(i) as Element)
                }
                MascotConfig(actions = actions)
            }
            "behaviors" -> {
                val behaviorNodes = doc.getElementsByTagName("Behavior")
                val behaviors = (0 until behaviorNodes.length).map { i ->
                    parseBehavior(behaviorNodes.item(i) as Element)
                }
                MascotConfig(behaviors = behaviors)
            }
            else -> throw IllegalArgumentException("Unknown type: $type")
        }

        yamlPath.outputStream().use { out ->
            yamlMapper.writeValue(out, config)
        }
    }

    private fun parseAction(element: Element): ActionConfig {
        val name = element.getAttribute("Name")
        val type = element.getAttribute("Type")
        val classPattern = if (element.hasAttribute("Class")) element.getAttribute("Class") else null
        
        val params = mutableMapOf<String, Any>()

        // Attributes
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val attrName = attr.nodeName
            if (attrName !in setOf("Name", "Type", "Class")) {
                params[attrName] = parseValue(attr.nodeValue)
            }
        }

        // Children
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                when (node.tagName) {
                    "Animation" -> {
                        val poses = mutableListOf<Map<String, Any>>()
                        val poseNodes = node.getElementsByTagName("Pose")
                        for (j in 0 until poseNodes.length) {
                            val pose = poseNodes.item(j) as Element
                            val poseMap = mutableMapOf<String, Any>()
                            val poseAttrs = pose.attributes
                            for (k in 0 until poseAttrs.length) {
                                val attr = poseAttrs.item(k)
                                poseMap[attr.nodeName] = parseValue(attr.nodeValue)
                            }
                            poses.add(poseMap)
                        }
                        params["Animation"] = poses
                    }
                    "ActionReference" -> {
                        @Suppress("UNCHECKED_CAST")
                        val refs = params.getOrPut("ActionReferences") { mutableListOf<String>() } as MutableList<String>
                        refs.add(node.getAttribute("Name"))
                    }
                    "Point" -> {
                        val pointMap = mutableMapOf<String, Any>()
                        val pointAttrs = node.attributes
                        for (k in 0 until pointAttrs.length) {
                            val attr = pointAttrs.item(k)
                            pointMap[attr.nodeName] = parseValue(attr.nodeValue)
                        }
                        params["Point"] = pointMap
                    }
                }
            }
        }

        return ActionConfig(name, type, classPattern, params)
    }

    private fun parseBehavior(element: Element): BehaviorConfig {
        val name = element.getAttribute("Name")
        val frequency = element.getAttribute("Frequency").toIntOrNull() ?: 0
        val conditions = mutableListOf<String>()
        val actions = mutableListOf<String>()
        var nextBehavior: String? = null

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                when (node.tagName) {
                    "Condition" -> conditions.add(node.textContent.trim())
                    "ActionReference" -> actions.add(node.getAttribute("Name"))
                    "NextBehavior" -> nextBehavior = node.getAttribute("Name")
                }
            }
        }

        return BehaviorConfig(name, frequency, conditions, nextBehavior, actions)
    }

    private fun parseValue(value: String): Any {
        return value.toIntOrNull() ?: value.toDoubleOrNull() ?: value
    }
}