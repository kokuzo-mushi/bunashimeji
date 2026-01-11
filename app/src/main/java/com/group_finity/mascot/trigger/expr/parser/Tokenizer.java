package com.group_finity.mascot.trigger.expr.parser;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {
    private final String source;
    private int pos;
    private final List<Token> tokens = new ArrayList<>();

    public Tokenizer(String source) {
        this.source = source;
        this.pos = 0;
    }

    public List<Token> tokenize() {
        while (pos < source.length()) {
            char c = source.charAt(pos);

            if (Character.isWhitespace(c)) {
                pos++;
            } else if (Character.isDigit(c)) {
                readNumber();
            } else if (Character.isLetter(c)) {
                readWord();
            } else if (c == '\'' || c == '"') {
                readString(c);
            } else {
                readSymbol();
            }
        }
        return tokens;
    }

    private void readNumber() {
        int start = pos;
        while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
            pos++;
        }
        tokens.add(new Token(TokenType.NUMBER, source.substring(start, pos)));
    }

    private void readWord() {
        int start = pos;
        while (pos < source.length() && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
            pos++;
        }
        String word = source.substring(start, pos);
        switch (word) {
            case "true":
            case "false":
            case "null":
                tokens.add(new Token(TokenType.KEYWORD, word));
                break;
            case "and":
            case "or":
                tokens.add(new Token(TokenType.SYMBOL, word));
                break;
            default:
                tokens.add(new Token(TokenType.IDENTIFIER, word));
                break;
        }
    }

    private void readString(char quote) {
        pos++; // skip opening quote
        int start = pos;
        while (pos < source.length() && source.charAt(pos) != quote) {
            pos++;
        }
        tokens.add(new Token(TokenType.STRING, source.substring(start, pos)));
        if (pos < source.length()) {
            pos++; // skip closing quote
        }
    }

    private void readSymbol() {
        char c = source.charAt(pos);
        String sym = String.valueOf(c);
        pos++;

        // 3文字のシンボルをチェック (===, !==)
        if (pos + 1 < source.length()) {
            char next = source.charAt(pos);
            char next2 = source.charAt(pos + 1);
            if ((c == '=' && next == '=' && next2 == '=') ||
                (c == '!' && next == '=' && next2 == '=')) {
                sym = sym + next + next2;
                pos += 2;
                tokens.add(new Token(TokenType.SYMBOL, sym));
                return;
            }
        }

        // 2文字のシンボルをチェック (==, !=, <=, >=, &&, ||)
        if (pos < source.length()) {
            char next = source.charAt(pos);
            if ((c == '=' && next == '=') ||
                (c == '!' && next == '=') ||
                (c == '<' && next == '=') ||
                (c == '>' && next == '=') ||
                (c == '&' && next == '&') ||
                (c == '|' && next == '|')) {
                sym += next;
                pos++;
            }
        }
        tokens.add(new Token(TokenType.SYMBOL, sym));
    }

    public static class Token {
        public final TokenType type;
        public final String text;

        public Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }
        
        public String getText() { return text; }
    }

    public enum TokenType {
        NUMBER, STRING, IDENTIFIER, KEYWORD, SYMBOL
    }
}