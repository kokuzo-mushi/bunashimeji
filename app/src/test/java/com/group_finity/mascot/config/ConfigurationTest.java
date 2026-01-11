package com.group_finity.mascot.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXParseException;

import jakarta.xml.bind.annotation.XmlRootElement;

class ConfigurationTest {

    @TempDir
    Path tempDir;

    // テスト用のダミー設定クラス
    @XmlRootElement(name = "config")
    static class TestConfig {
    }

    @Test
    void load_ShouldThrowException_WhenXmlContainsDoctypeDeclaration() throws IOException {
        // Arrange
        // XXE攻撃を模したXML (DOCTYPE宣言を含む)
        // XmlSecurityの設定により、DOCTYPE宣言自体が禁止されているため、
        // 外部エンティティの解決以前に、この宣言の時点でエラーになるはずである。
        String xxeXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE config [
                  <!ELEMENT config ANY >
                  <!ENTITY xxe SYSTEM "file:///etc/passwd" >]>
                <config>&xxe;</config>
                """;
        Path xmlPath = tempDir.resolve("xxe.xml");
        Files.writeString(xmlPath, xxeXml);

        Configuration config = new Configuration();

        // Act & Assert
        // SAXParseException がスローされることを確認
        Exception exception = assertThrows(SAXParseException.class, () -> {
            config.load(xmlPath, TestConfig.class);
        });

        // エラーメッセージに "DOCTYPE" が禁止されている旨が含まれているか確認
        assertTrue(exception.getMessage().contains("DOCTYPE"), 
            "Security configuration should disallow DOCTYPE declarations. Actual message: " + exception.getMessage());
    }

    @Test
    void load_ShouldSucceed_WhenXmlIsValid() throws Exception {
        // Arrange
        String validXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><config/>";
        Path xmlPath = tempDir.resolve("valid.xml");
        Files.writeString(xmlPath, validXml);

        // Act
        TestConfig result = new Configuration().load(xmlPath, TestConfig.class);

        // Assert
        assertNotNull(result);
    }
}