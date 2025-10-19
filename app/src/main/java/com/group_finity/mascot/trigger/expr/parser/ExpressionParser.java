package com.group_finity.mascot.trigger.expr.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.group_finity.mascot.trigger.expr.node.BinaryExpressionNode;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.node.LiteralNode;
import com.group_finity.mascot.trigger.expr.node.UnaryExpressionNode;
import com.group_finity.mascot.trigger.expr.node.VariableNode;

/**
 * 式パーサ：簡易式構文をRPN(逆ポーランド記法)経由でASTに構築
 * 対応演算子: +, -, *, /, %, <, <=, >, >=, ==, !=, &&, ||, !, ===, 単項+, 単項-
 */
public final class ExpressionParser {

    private final String exprText;

    public ExpressionParser(String exprText) {
        this.exprText = exprText;
    }

    public ExpressionNode parse() {
        List<String> tokens = tokenize(exprText);
        List<String> rpn = toRPN(tokens);
        return buildAST(rpn);
    }

    // ===============================
    // トークナイズ
    // ===============================
    private List<String> tokenize(String expr) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);

            if (Character.isWhitespace(c)) {
                if (sb.length() > 0) {
                    result.add(sb.toString());
                    sb.setLength(0);
                }
                continue;
            }

            // 演算子検出
            if ("=!<>&|+-*/%()".indexOf(c) >= 0) {
                if (sb.length() > 0) {
                    result.add(sb.toString());
                    sb.setLength(0);
                }

                // 三文字演算子対応 (例: ===)
                if (i + 2 < expr.length()) {
                    String three = "" + c + expr.charAt(i + 1) + expr.charAt(i + 2);
                    if ("===".equals(three)) {
                        result.add(three);
                        i += 2;
                        continue;
                    }
                }

                // 二文字演算子対応
                if (i + 1 < expr.length()) {
                    String two = "" + c + expr.charAt(i + 1);
                    if (List.of("==", "!=", "<=", ">=", "&&", "||").contains(two)) {
                        result.add(two);
                        i++;
                        continue;
                    }
                }

                // 一文字演算子
                result.add(String.valueOf(c));
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) result.add(sb.toString());
        return result;
    }


    // ===============================
    // RPN変換 (Shunting Yardアルゴリズム)
    // ===============================
    private List<String> toRPN(List<String> tokens) {
        // 優先順位: 1 (単項), 2 (乗除), 3 (加減), 4 (比較), 5 (抽象等価), 5 (厳密等価), 6 (AND), 7 (OR)
        Map<String, Integer> prec = Map.ofEntries(
            Map.entry("u-", 1), Map.entry("u+", 1), Map.entry("!", 1), // u-: 単項マイナス, u+: 単項プラス
            Map.entry("*", 2), Map.entry("/", 2), Map.entry("%", 2),
            Map.entry("+", 3), Map.entry("-", 3),
            Map.entry("<", 4), Map.entry("<=", 4), Map.entry(">", 4), Map.entry(">=", 4),
            Map.entry("==", 5), Map.entry("!=", 5), Map.entry("===", 5), // '===' を追加
            Map.entry("&&", 6),
            Map.entry("||", 7)
        );

        List<String> output = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);

            if (prec.containsKey(token) || "===".equals(token)) {
                String operator = token;

                // 単項演算子の検出 (前が演算子、開き括弧、または式の最初の場合)
                boolean isUnary = (i == 0 || isOperatorOrParen(tokens.get(i - 1)));

                if (operator.equals("-") && isUnary) {
                    operator = "u-"; // 単項マイナス
                } else if (operator.equals("+") && isUnary) {
                    operator = "u+"; // 単項プラス
                }
                
                // RPNスタック処理
                if (prec.containsKey(operator)) {
                    while (!stack.isEmpty() && prec.containsKey(stack.peek())
                            && prec.get(stack.peek()) < prec.get(operator)) {
                        output.add(stack.pop());
                    }
                    stack.push(operator);
                } else {
                    // === がprecに含まれていない場合（上記のMap定義で含まれるようにしたが、念のため）
                    stack.push(operator);
                }

            } else if ("(".equals(token)) {
                stack.push(token);
            } else if (")".equals(token)) {
                while (!stack.isEmpty() && !"(".equals(stack.peek())) {
                    output.add(stack.pop());
                }
                if (!stack.isEmpty() && "(".equals(stack.peek())) stack.pop(); // '(' を捨てる
                else throw new RuntimeException("Mismatched parentheses");
            } else {
                output.add(token);
            }
        }

        while (!stack.isEmpty()) {
            if ("(".equals(stack.peek()) || ")".equals(stack.peek())) throw new RuntimeException("Mismatched parentheses");
            output.add(stack.pop());
        }
        return output;
    }

    private boolean isOperatorOrParen(String token) {
        return List.of("(", "+", "-", "*", "/", "%", "<", ">", "<=", ">=", "==", "!=", "===", "&&", "||", "!", "u-", "u+").contains(token);
    }

    // ===============================
    // AST構築
    // ===============================
    private ExpressionNode buildAST(List<String> rpn) {
        Deque<ExpressionNode> stack = new ArrayDeque<>();

        for (String token : rpn) {
            switch (token) {
                // 二項演算子
                case "+": case "-": case "*": case "/":
                case "%": case "<": case ">": case "<=": case ">=":
                case "==": case "!=": case "&&": case "||": 
                case "===": { // '===' を追加
                    if (stack.size() < 2) throw new RuntimeException("Syntax Error: missing operands for " + token);
                    ExpressionNode right = stack.pop();
                    ExpressionNode left = stack.pop();
                    stack.push(new BinaryExpressionNode(token, left, right));
                    break;
                }
                // 単項演算子
                case "!":
                    if (stack.isEmpty()) throw new RuntimeException("Syntax Error: missing operand for !");
                    stack.push(new UnaryExpressionNode("!", stack.pop()));
                    break;
                case "u-":
                    if (stack.isEmpty()) throw new RuntimeException("Syntax Error: missing operand for u-");
                    // 修正: Double.class -> Object.class
                    stack.push(new UnaryExpressionNode("-", stack.pop())); 
                    break;
                case "u+":
                    if (stack.isEmpty()) throw new RuntimeException("Syntax Error: missing operand for u+");
                    // 修正: Double.class -> Object.class
                    stack.push(new UnaryExpressionNode("+", stack.pop()));
                    break;
                default:
                    stack.push(literalOrVariable(token));
            }
        }

        if (stack.size() != 1) throw new RuntimeException("Syntax Error: invalid expression");
        return stack.pop();
    }

    // ===============================
    // リテラル or 変数ノード
    // ===============================
    private ExpressionNode literalOrVariable(String token) {
        if ("true".equalsIgnoreCase(token)) return new LiteralNode(true, Boolean.class);
        if ("false".equalsIgnoreCase(token)) return new LiteralNode(false, Boolean.class);

        // クォートされた文字列リテラル対応
        if (token.startsWith("\"") && token.endsWith("\"")) {
             String inner = token.substring(1, token.length() - 1);
             return new LiteralNode(inner, String.class);
        }
        // シングルクォート対応は元コードのコメントアウトされており、ここでは削除。
        // もし必要なら元のコードの通り追加してください。

        try {
            if (token.contains(".")) return new LiteralNode(Double.parseDouble(token), Double.class);
            return new LiteralNode(Long.parseLong(token), Long.class);
        } catch (NumberFormatException e) {
            return new VariableNode(token, null);
        }
    }
    
}
