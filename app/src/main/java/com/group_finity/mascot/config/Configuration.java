package com.group_finity.mascot.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import com.group_finity.mascot.config.xml.XmlActionList;
import com.group_finity.mascot.config.xml.XmlBehaviorList;

/**
 * アプリケーションの設定ファイルを読み込むクラス。
 * JAXBを使用し、XMLファイルからオブジェクトへのマッピングを行う。
 */
public class Configuration {

    /**
     * 指定されたパスのXMLファイルを読み込み、指定された型のオブジェクトとして返します。
     * XXE対策のため、XmlSecurityを使用して生成されたパーサーを経由します。
     *
     * @param <T> マッピング対象の型
     * @param path 読み込むXMLファイルのパス
     * @param type マッピング対象のクラス
     * @return 読み込まれたオブジェクト
     * @throws JAXBException JAXBによるアンマーシャルに失敗した場合
     * @throws IOException ファイル読み込みに失敗した場合
     * @throws ParserConfigurationException XMLパーサーの設定に失敗した場合
     * @throws SAXException XMLのパースに失敗した場合
     */
    public <T> T load(Path path, Class<T> type) throws JAXBException, IOException, ParserConfigurationException, SAXException {
        // 1. セキュアなDocumentBuilderFactoryを取得 (XXE対策)
        DocumentBuilderFactory dbf = XmlSecurity.createSecureFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();

        // デフォルトのエラーハンドラはコンソールに[Fatal Error]を出力することがあるため、
        // 明示的にハンドラを設定して出力を抑制し、例外のみをスローさせる
        db.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                // 警告は無視、または必要に応じてログ出力
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });

        try (InputStream is = Files.newInputStream(path)) {
            // 2. XMLをパースしてDOM Documentを取得
            Document doc = db.parse(is);

            // 3. JAXBでアンマーシャル
            JAXBContext context = JAXBContext.newInstance(type);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            // unmarshal(Node, Class) は JAXBElement<T> を返すため、getValue() で中身を取り出す
            return unmarshaller.unmarshal(doc, type).getValue();
        }
    }

    /**
     * actions.xml を読み込み、アクション定義のリストを返します。
     *
     * @param path actions.xml のパス
     * @return アクション定義リスト
     */
    public XmlActionList loadActions(Path path) throws JAXBException, IOException, ParserConfigurationException, SAXException {
        return load(path, XmlActionList.class);
    }

    /**
     * behaviors.xml を読み込み、振る舞い定義のリストを返します。
     *
     * @param path behaviors.xml のパス
     * @return 振る舞い定義リスト
     */
    public XmlBehaviorList loadBehaviors(Path path) throws JAXBException, IOException, ParserConfigurationException, SAXException {
        return load(path, XmlBehaviorList.class);
    }
}