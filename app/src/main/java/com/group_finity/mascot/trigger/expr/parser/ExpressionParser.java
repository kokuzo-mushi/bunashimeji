package com.group_finity.mascot.trigger.expr.parser;

import java.util.ArrayList;
import java.util.List;

import com.group_finity.mascot.trigger.expr.node.BinaryExpressionNode;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.node.LiteralNode;
import com.group_finity.mascot.trigger.expr.node.UnaryExpressionNode;
import com.group_finity.mascot.trigger.expr.node.VariableNode;

/**
 * ExpressionParser (統合版)
 * - インスタンス/静的呼び出しの両対応
 * - Binary / Unary / Literal / Variable ノードに完全整合
 */
public final class ExpressionParser {

    private final List<Token> tokens;
    private int pos;

    public ExpressionParser(String expr) {
        this.tokens = tokenize(expr);
        this.pos = 0;
    }

    /** 静的パーサ入口（両方の呼び出しスタイルに対応） */
    public static ExpressionNode parse(String expr) {
        ExpressionParser p = new ExpressionParser(expr);
        return p.parse();
    }

    /** インスタンス版パース（再帰下降） */
    public ExpressionNode parse() {
        ExpressionNode node = parseOr();
        // EOFはtokenizeで付与済み。二重チェックはしない。
        return node;
    }

    // ===== 構文解析 =====
    private ExpressionNode parseOr() {
        ExpressionNode left = parseAnd();
        while (match(TokenType.OROR)) {
            String op = previous().lexeme;
            ExpressionNode right = parseAnd();
            left = new BinaryExpressionNode(left, op, right);
        }
        return left;
    }

    private ExpressionNode parseAnd() {
        ExpressionNode left = parseEquality();
        while (match(TokenType.ANDAND)) {
            String op = previous().lexeme;
            ExpressionNode right = parseEquality();
            left = new BinaryExpressionNode(left, op, right);
        }
        return left;
    }

    private ExpressionNode parseEquality() {
        ExpressionNode left = parseComparison();
        while (match(TokenType.EQEQ) || match(TokenType.BANGEQ)
            || match(TokenType.EQEQEQ) || match(TokenType.BANGEQEQ)) {
            String op = previous().lexeme;
            ExpressionNode right = parseComparison();
            left = new BinaryExpressionNode(left, op, right);
        }
        return left;
    }

    private ExpressionNode parseComparison() {
        ExpressionNode left = parseAdditive();
        while (match(TokenType.LT) || match(TokenType.LTE)
            || match(TokenType.GT) || match(TokenType.GTE)) {
            String op = previous().lexeme;
            ExpressionNode right = parseAdditive();
            left = new BinaryExpressionNode(left, op, right);
        }
        return left;
    }

    private ExpressionNode parseAdditive() {
        ExpressionNode left = parseMultiplicative();
        while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
            String op = previous().lexeme;
            ExpressionNode right = parseMultiplicative();
            left = new BinaryExpressionNode(left, op, right);
        }
        return left;
    }

    private ExpressionNode parseMultiplicative() {
        ExpressionNode left = parseUnary();
        while (match(TokenType.STAR) || match(TokenType.SLASH) || match(TokenType.PERCENT)) {
            String op = previous().lexeme;
            ExpressionNode right = parseUnary();
            left = new BinaryExpressionNode(left, op, right);
        }
        return left;
    }

    private ExpressionNode parseUnary() {
        if (match(TokenType.PLUS) || match(TokenType.MINUS)
            || match(TokenType.BANG) || match(TokenType.TILDE)) {
            String op = previous().lexeme;
            ExpressionNode right = parseUnary(); // ← 再帰呼び出し順OK
            return new UnaryExpressionNode(op, right);
        }
        return parsePrimary(); // ← 括弧含む最下層呼び出し
    }

    private ExpressionNode parsePrimary() {
        if (match(TokenType.NUMBER)) {
            String s = previous().lexeme;
            if (s.contains(".")) return new LiteralNode(Double.parseDouble(s));
            try { return new LiteralNode(Long.parseLong(s)); }
            catch (NumberFormatException e) { return new LiteralNode(Double.parseDouble(s)); }
        }
        if (match(TokenType.STRING)) {
            String raw = previous().lexeme;
            return new LiteralNode(unescape(raw.substring(1, raw.length() - 1)));
        }
        if (match(TokenType.TRUE))  return new LiteralNode(Boolean.TRUE);
        if (match(TokenType.FALSE)) return new LiteralNode(Boolean.FALSE);
        if (match(TokenType.IDENT)) return new VariableNode(previous().lexeme);
        if (match(TokenType.LPAREN)) {
            ExpressionNode inside = parseOr();
            expect(TokenType.RPAREN); // ★ advance削除
            return inside;
        }
        throw new RuntimeException("Unexpected token: " + peek().lexeme);
    }

    // ===== トークナイザ =====
    private static List<Token> tokenize(String src) {
        List<Token> ts = new ArrayList<>();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }

            // 数値
            if (Character.isDigit(c)) {
                int start = i;
                while (i < src.length() && (Character.isDigit(src.charAt(i)) || src.charAt(i)=='.')) i++;
                ts.add(new Token(TokenType.NUMBER, src.substring(start, i)));
                continue;
            }

            // 文字列（ダブルクォート）
            if (c == '"') {
                int start = ++i;
                StringBuilder sb = new StringBuilder();
                while (i < src.length() && src.charAt(i) != '"') {
                    sb.append(src.charAt(i++));
                }
                if (i < src.length() && src.charAt(i) == '"') i++;
                ts.add(new Token(TokenType.STRING, "\"" + sb + "\""));
                continue;
            }

            // 文字列（シングルクォート）
            if (c == '\'') {
                int start = ++i;
                StringBuilder sb = new StringBuilder();
                while (i < src.length() && src.charAt(i) != '\'') {
                    sb.append(src.charAt(i++));
                }
                if (i < src.length() && src.charAt(i) == '\'') i++;
                ts.add(new Token(TokenType.STRING, "\"" + sb + "\"")); // 内部表現はダブルクォートに統一
                continue;
            }

            // 識別子
            if (Character.isLetter(c) || c == '_') {
                int start = i++;
                while (i < src.length() && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i)=='_')) i++;
                String word = src.substring(start, i);
                switch (word) {
                    case "true" -> ts.add(new Token(TokenType.TRUE, word));
                    case "false" -> ts.add(new Token(TokenType.FALSE, word));
                    default -> ts.add(new Token(TokenType.IDENT, word));
                }
                continue;
            }

            // 演算子（3→2→1 の順で貪欲に）
            String two = (i + 1 < src.length()) ? src.substring(i, i + 2) : "";
            String three = (i + 2 < src.length()) ? src.substring(i, i + 3) : "";

            switch (three) {
                case "===" -> { ts.add(new Token(TokenType.EQEQEQ, three)); i += 3; continue; }
                case "!==" -> { ts.add(new Token(TokenType.BANGEQEQ, three)); i += 3; continue; }
            }
            switch (two) {
                case "==" -> { ts.add(new Token(TokenType.EQEQ, two)); i += 2; continue; }
                case "!=" -> { ts.add(new Token(TokenType.BANGEQ, two)); i += 2; continue; }
                case "<=" -> { ts.add(new Token(TokenType.LTE, two)); i += 2; continue; }
                case ">=" -> { ts.add(new Token(TokenType.GTE, two)); i += 2; continue; }
                case "&&" -> { ts.add(new Token(TokenType.ANDAND, two)); i += 2; continue; }
                case "||" -> { ts.add(new Token(TokenType.OROR, two)); i += 2; continue; }
            }

            switch (c) {
                case '+': ts.add(new Token(TokenType.PLUS, "+")); break;
                case '-': ts.add(new Token(TokenType.MINUS, "-")); break;
                case '*': ts.add(new Token(TokenType.STAR, "*")); break;
                case '/': ts.add(new Token(TokenType.SLASH, "/")); break;
                case '%': ts.add(new Token(TokenType.PERCENT, "%")); break;
                case '<': ts.add(new Token(TokenType.LT, "<")); break;
                case '>': ts.add(new Token(TokenType.GT, ">")); break;
                case '!': ts.add(new Token(TokenType.BANG, "!")); break;
                case '~': ts.add(new Token(TokenType.TILDE, "~")); break;
                case '(': ts.add(new Token(TokenType.LPAREN, "(")); break;
                case ')': ts.add(new Token(TokenType.RPAREN, ")")); break;
                default: throw new RuntimeException("Unexpected char: " + c);
            }
            i++;
        }
        ts.add(new Token(TokenType.EOF, ""));
        return ts;
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    // ===== 内部トークン管理 =====
    private boolean match(TokenType type) {
        if (check(type)) { advance(); return true; }
        return false;
    }
    private boolean check(TokenType type) { return !isAtEnd() && peek().type == type; }
    private Token advance() { if (!isAtEnd()) pos++; return previous(); }
    private boolean isAtEnd() { return peek().type == TokenType.EOF; }
    private Token peek() { return tokens.get(pos); }
    private Token previous() { return tokens.get(pos - 1); }
    private void expect(TokenType type) {
        if (!match(type)) throw new RuntimeException("Expected " + type + " but got " + peek().type);
    }

    // ===== 内部型 =====
    private enum TokenType {
        PLUS, MINUS, STAR, SLASH, PERCENT,
        LT, LTE, GT, GTE,
        EQEQ, BANGEQ, EQEQEQ, BANGEQEQ,
        ANDAND, OROR,
        BANG, TILDE,
        LPAREN, RPAREN,
        NUMBER, STRING, TRUE, FALSE, IDENT, EOF
    }

    private static final class Token {
        final TokenType type;
        final String lexeme;
        Token(TokenType type, String lexeme) { this.type = type; this.lexeme = lexeme; }
    }
}
