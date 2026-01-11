package com.group_finity.mascot.trigger.expr.parser;

import com.group_finity.mascot.trigger.expr.ast.*;
import com.group_finity.mascot.trigger.expr.parser.Tokenizer.Token;
import com.group_finity.mascot.trigger.expr.parser.Tokenizer.TokenType;

import java.util.List;

public class ExpressionParser {
    private final List<Token> tokens;
    private int pos;

    public ExpressionParser(String source) {
        this.tokens = new Tokenizer(source).tokenize();
        this.pos = 0;
    }

    public Expression parse() {
        Expression expr = parseExpression();
        if (pos < tokens.size()) {
            throw new RuntimeException("Unexpected token at the end: " + tokens.get(pos).getText());
        }
        return expr;
    }

    // Expression -> LogicalOr
    private Expression parseExpression() {
        return parseLogicalOr();
    }

    // LogicalOr -> LogicalAnd ( ('or'|'||') LogicalAnd )*
    private Expression parseLogicalOr() {
        Expression left = parseLogicalAnd();
        while (match("or") || match("||")) {
            String op = previous().getText();
            Expression right = parseLogicalAnd();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    // LogicalAnd -> Equality ( ('and'|'&&') Equality )*
    private Expression parseLogicalAnd() {
        Expression left = parseEquality();
        while (match("and") || match("&&")) {
            String op = previous().getText();
            Expression right = parseEquality();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    // Equality -> Relational ( ('=='|'!=') Relational )*
    private Expression parseEquality() {
        Expression left = parseRelational();
        while (match("==") || match("!=") || match("===") || match("!==")) {
            String op = previous().getText();
            Expression right = parseRelational();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    // Relational -> Additive ( ('<'|'<='|'>'|'>=') Additive )*
    private Expression parseRelational() {
        Expression left = parseAdditive();
        while (match("<") || match("<=") || match(">") || match(">=")) {
            String op = previous().getText();
            Expression right = parseAdditive();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    // Additive -> Multiplicative ( ('+'|'-') Multiplicative )*
    private Expression parseAdditive() {
        Expression left = parseMultiplicative();
        while (match("+") || match("-")) {
            String op = previous().getText();
            Expression right = parseMultiplicative();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    // Multiplicative -> Unary ( ('*'|'/'|'%') Unary )*
    private Expression parseMultiplicative() {
        Expression left = parseUnary();
        while (match("*") || match("/") || match("%")) {
            String op = previous().getText();
            Expression right = parseUnary();
            left = new BinaryExpression(left, op, right);
        }
        return left;
    }

    // Unary -> ('!'|'-'|'+') Unary | Primary
    private Expression parseUnary() {
        if (match("!") || match("-") || match("+")) {
            String op = previous().getText();
            Expression right = parseUnary();
            return new UnaryExpression(op, right);
        }
        return parsePrimary();
    }

    // Primary -> Literal | Identifier | '(' Expression ')'
    private Expression parsePrimary() {
        if (match(TokenType.NUMBER)) {
            String text = previous().getText();
            if (text.contains(".") || text.contains("e") || text.contains("E")) {
                return new LiteralExpression(Double.parseDouble(text));
            } else {
                try {
                    return new LiteralExpression(Integer.parseInt(text));
                } catch (NumberFormatException e) {
                    return new LiteralExpression(Long.parseLong(text));
                }
            }
        }
        if (match(TokenType.STRING)) {
            return new LiteralExpression(previous().getText());
        }
        if (match("true"))
            return new LiteralExpression(true);
        if (match("false"))
            return new LiteralExpression(false);
        if (match("null"))
            return new LiteralExpression(null);

        if (match(TokenType.IDENTIFIER)) {
            Expression expr = new VariableExpression(previous().getText());
            while (match(".")) {
                if (match(TokenType.IDENTIFIER)) {
                    expr = new MemberAccessExpression(expr, previous().getText());
                } else {
                    throw new RuntimeException("Identifier expected after '.'");
                }
            }
            return expr;
        }

        if (match("(")) {
            Expression expr = parseExpression();
            if (!match(")")) {
                throw new RuntimeException("Expected ')' after expression");
            }
            return expr;
        }

        throw new RuntimeException("Unexpected token: " + (pos < tokens.size() ? tokens.get(pos).getText() : "EOF"));
    }

    private boolean match(String text) {
        if (pos < tokens.size() && tokens.get(pos).getText().equals(text)) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean match(TokenType type) {
        if (pos < tokens.size() && tokens.get(pos).type == type) {
            pos++;
            return true;
        }
        return false;
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }
}
