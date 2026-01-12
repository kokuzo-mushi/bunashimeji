package com.group_finity.mascot.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YamlCapabilityTest {

    data class TestConfig(val name: String, val version: Double)

    @Test
    fun `should parse yaml correctly using jackson`() {
        // Arrange
        val yaml = """
            name: ShimejiNeo
            version: 5.0
        """.trimIndent()

        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

        // Act
        val config = mapper.readValue(yaml, TestConfig::class.java)

        // Assert
        assertEquals("ShimejiNeo", config.name)
        assertEquals(5.0, config.version)
    }
}