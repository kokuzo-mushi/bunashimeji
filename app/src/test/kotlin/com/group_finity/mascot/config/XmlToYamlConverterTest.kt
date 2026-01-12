package com.group_finity.mascot.config

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class XmlToYamlConverterTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should convert actions xml to yaml`() {
        val xml = """
            <Actions>
                <Action Name="Walk" Type="Walk" Speed="1">
                    <Animation>
                        <Pose ImageAnchor="64,128" Duration="200" />
                    </Animation>
                </Action>
                <Action Name="Sequence" Type="Sequence">
                    <ActionReference Name="Walk" />
                </Action>
            </Actions>
        """.trimIndent()
        val xmlPath = tempDir.resolve("actions.xml")
        xmlPath.writeText(xml)
        val yamlPath = tempDir.resolve("actions.yaml")

        XmlToYamlConverter.convert(xmlPath, yamlPath, "actions")

        val yaml = yamlPath.readText()
        println(yaml)
        assertTrue(yaml.contains("name: \"Walk\""))
        assertTrue(yaml.contains("type: \"Walk\""))
        assertTrue(yaml.contains("Speed: 1"))
        assertTrue(yaml.contains("Animation:"))
        assertTrue(yaml.contains("ActionReferences:"))
        assertTrue(yaml.contains("- \"Walk\""))
    }

    @Test
    fun `should convert behaviors xml to yaml`() {
        val xml = """
            <Behaviors>
                <Behavior Name="Fall" Frequency="100">
                    <Condition>mascot.isGrounded() == false</Condition>
                    <ActionReference Name="Fall" />
                </Behavior>
            </Behaviors>
        """.trimIndent()
        val xmlPath = tempDir.resolve("behaviors.xml")
        xmlPath.writeText(xml)
        val yamlPath = tempDir.resolve("behaviors.yaml")

        XmlToYamlConverter.convert(xmlPath, yamlPath, "behaviors")

        val yaml = yamlPath.readText()
        println(yaml)
        assertTrue(yaml.contains("name: \"Fall\""))
        assertTrue(yaml.contains("frequency: 100"))
        assertTrue(yaml.contains("conditions:"))
        assertTrue(yaml.contains("- \"mascot.isGrounded() == false\""))
        assertTrue(yaml.contains("actions:"))
        assertTrue(yaml.contains("- \"Fall\""))
    }
}