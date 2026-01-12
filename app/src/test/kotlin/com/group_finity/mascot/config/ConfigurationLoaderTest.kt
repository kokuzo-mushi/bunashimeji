package com.group_finity.mascot.config

import jakarta.xml.bind.annotation.XmlRootElement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xml.sax.SAXParseException
import java.nio.file.Path
import kotlin.io.path.writeText

class ConfigurationLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    // XML(JAXB)とYAML(Jackson)の両方でテストするためのダミークラス
    @XmlRootElement(name = "config")
    class TestConfig {
        var name: String = ""
        var version: Double = 0.0
    }

    @Test
    fun `load should parse YAML correctly`() {
        // Arrange
        val yaml = """
            name: ShimejiNeo
            version: 5.0
        """.trimIndent()
        val yamlPath = tempDir.resolve("config.yaml")
        yamlPath.writeText(yaml)

        val configLoader = ConfigurationLoader()

        // Act
        // 拡張子が .yaml なので Jackson が使われるはず
        val result = configLoader.load(yamlPath, TestConfig::class.java)

        // Assert
        assertEquals("ShimejiNeo", result.name)
        assertEquals(5.0, result.version)
    }

    @Test
    fun `load should parse XML correctly`() {
        // Arrange
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <config>
                <name>ShimejiClassic</name>
                <version>1.0</version>
            </config>
        """.trimIndent()
        val xmlPath = tempDir.resolve("config.xml")
        xmlPath.writeText(xml)

        val configLoader = ConfigurationLoader()

        // Act
        // 拡張子が .xml なので JAXB が使われるはず
        val result = configLoader.load(xmlPath, TestConfig::class.java)

        // Assert
        assertEquals("ShimejiClassic", result.name)
        assertEquals(1.0, result.version)
    }

    @Test
    fun `load should throw exception for XML with DOCTYPE (XXE protection)`() {
        // Arrange
        val xxeXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE config [
              <!ELEMENT config ANY >
              <!ENTITY xxe SYSTEM "file:///etc/passwd" >]>
            <config>&xxe;</config>
        """.trimIndent()
        val xmlPath = tempDir.resolve("xxe.xml")
        xmlPath.writeText(xxeXml)

        val configLoader = ConfigurationLoader()

        // Act & Assert
        val exception = assertThrows(SAXParseException::class.java) {
            configLoader.load(xmlPath, TestConfig::class.java)
        }
        assertTrue(exception.message?.contains("DOCTYPE") == true, 
            "Security configuration should disallow DOCTYPE declarations. Actual: ${exception.message}")
    }
}