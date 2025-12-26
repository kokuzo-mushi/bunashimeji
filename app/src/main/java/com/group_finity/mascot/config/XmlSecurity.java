package com.group_finity.mascot.config;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * XML処理におけるセキュリティ設定を提供するユーティリティクラス。
 * XXE (XML External Entity) 攻撃などの脆弱性を防止するための設定を行う。
 */
public class XmlSecurity {

    /**
     * XXE対策が施されたセキュアな DocumentBuilderFactory を生成して返します。
     *
     * @return セキュアに設定された DocumentBuilderFactory
     * @throws ParserConfigurationException 設定に失敗した場合
     */
    public static DocumentBuilderFactory createSecureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        // DTD宣言自体を禁止する (最も安全な設定)
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        // 外部エンティティの解決を禁止
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        // 外部DTDのロードを禁止
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);

        return dbf;
    }
}