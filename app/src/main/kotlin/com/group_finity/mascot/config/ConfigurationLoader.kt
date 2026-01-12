package com.group_finity.mascot.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.group_finity.mascot.config.xml.XmlActionList
import com.group_finity.mascot.config.xml.XmlBehaviorList
import jakarta.xml.bind.JAXBContext
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension
import kotlin.io.path.reader

/**
 * アプリケーションの設定ファイルを読み込むクラス。
 * XML (JAXB) と YAML (Jackson) の両方をサポートし、拡張子によって自動的に切り替える。
 */
class ConfigurationLoader {

    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    /**
     * 指定されたパスの設定ファイルを読み込み、指定された型のオブジェクトとして返します。
     * 拡張子が .yaml / .yml の場合は Jackson を、それ以外は JAXB (XML) を使用します。
     */
    fun <T> load(path: Path, type: Class<T>): T {
        return if (isYaml(path)) {
            loadYaml(path, type)
        } else {
            loadXml(path, type)
        }
    }

    fun isYaml(path: Path): Boolean {
        val ext = path.extension.lowercase()
        return ext == "yaml" || ext == "yml"
    }

    private fun <T> loadYaml(path: Path, type: Class<T>): T {
        return path.reader().use { reader ->
            yamlMapper.readValue(reader, type)
        }
    }

    private fun <T> loadXml(path: Path, type: Class<T>): T {
        // XXE対策: セキュアなDocumentBuilderFactoryを構成
        // XmlSecurityクラスが利用できない場合に備えて直接設定
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false)
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        dbf.isXIncludeAware = false
        dbf.isExpandEntityReferences = false

        val db = dbf.newDocumentBuilder()
        db.setErrorHandler(object : ErrorHandler {
            override fun warning(exception: SAXParseException) {} // 無視
            override fun error(exception: SAXParseException) = throw exception
            override fun fatalError(exception: SAXParseException) = throw exception
        })

        Files.newInputStream(path).use { `is` ->
            val doc = db.parse(`is`)
            val context = JAXBContext.newInstance(type)
            val unmarshaller = context.createUnmarshaller()
            return unmarshaller.unmarshal(doc, type).value
        }
    }

    // --- 互換性メソッド ---

    fun loadActions(path: Path): XmlActionList {
        // TODO: YAMLの場合は MascotConfig -> XmlActionList への変換が必要になるが、
        // 現段階ではXML読み込みの互換性を維持する。
        return load(path, XmlActionList::class.java)
    }

    fun loadBehaviors(path: Path): XmlBehaviorList {
        return load(path, XmlBehaviorList::class.java)
    }

    fun loadMascotConfig(path: Path): MascotConfig {
        return load(path, MascotConfig::class.java)
    }
}