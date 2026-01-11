package com.group_finity.mascot.trigger.expr.type;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public class DefaultTypeResolver implements TypeResolver {

    @Override
    public Object applyUnaryOp(String operator, Object value) {
        return switch (operator) {
            case "!" -> !TypeResolver.toBoolean(value);
            case "-" -> {
                Number num = TypeResolver.toNumber(value);
                if (num == null)
                    yield null;
                yield new BigDecimal(num.toString()).negate();
            }
            case "+" -> TypeResolver.toNumber(value); // No-op, just ensures it's a number
            default -> throw new IllegalArgumentException("Unknown unary operator: " + operator);
        };
    }

    @Override
    public Object applyBinaryOp(String operator, Object left, Object right) {
        // 1. 論理演算子
        if (operator.equals("&&")) {
            return TypeResolver.toBoolean(left) && TypeResolver.toBoolean(right);
        }
        if (operator.equals("||")) {
            return TypeResolver.toBoolean(left) || TypeResolver.toBoolean(right);
        }

        // 2. 算術演算子 (数値変換を優先)
        if (isArithmetic(operator)) {
            Number leftNum = TypeResolver.toNumber(left);
            Number rightNum = TypeResolver.toNumber(right);

            // 両方のオペランドが数値に変換できる場合
            if (leftNum != null && rightNum != null) {
                BigDecimal leftBd = new BigDecimal(leftNum.toString());
                BigDecimal rightBd = new BigDecimal(rightNum.toString());

                return switch (operator) {
                    case "+" -> leftBd.add(rightBd);
                    case "-" -> leftBd.subtract(rightBd);
                    case "*" -> leftBd.multiply(rightBd);
                    case "/" -> {
                        if (rightBd.compareTo(BigDecimal.ZERO) == 0)
                            yield null; // Division by zero
                        yield leftBd.divide(rightBd, 10, RoundingMode.HALF_UP);
                    }
                    case "%" -> {
                        if (rightBd.compareTo(BigDecimal.ZERO) == 0)
                            yield null; // Division by zero
                        yield leftBd.remainder(rightBd);
                    }
                    // isArithmeticでチェック済みのためdefaultは不要だが念のため
                    default -> throw new IllegalStateException("Unknown arithmetic operator: " + operator);
                };
            }

            // + 演算子の場合、数値に変換できなかったら文字列結合にフォールバック
            if (operator.equals("+")) {
                return String.valueOf(left) + String.valueOf(right);
            }

            // 他の算術演算子で数値に変換できなかった場合はエラー（nullを返す）
            return null;
        }

        // 3. 比較演算子
        if (isComparison(operator)) {
            int cmp = compare(left, right);
            return switch (operator) {
                case "==", "===" -> cmp == 0;
                case "!=", "!==" -> cmp != 0;
                case "<" -> cmp < 0;
                case "<=" -> cmp <= 0;
                case ">" -> cmp > 0;
                case ">=" -> cmp >= 0;
                default -> false; // Should not happen
            };
        }

        throw new IllegalArgumentException("Unknown binary operator: " + operator);
    }

    private boolean isComparison(String operator) {
        return switch (operator) {
            case "==", "===", "!=", "!==", "<", "<=", ">", ">=" -> true;
            default -> false;
        };
    }

    private boolean isArithmetic(String operator) {
        return switch (operator) {
            case "+", "-", "*", "/", "%" -> true;
            default -> false;
        };
    }

    private int compare(Object left, Object right) {
        if (Objects.equals(left, right)) {
            return 0;
        }
        if (left == null)
            return -1;
        if (right == null)
            return 1;

        // Numeric comparison
        if (left instanceof Number && right instanceof Number) {
            BigDecimal leftBd = new BigDecimal(left.toString());
            BigDecimal rightBd = new BigDecimal(right.toString());
            return leftBd.compareTo(rightBd);
        }

        // Fallback to string comparison
        return String.valueOf(left).compareTo(String.valueOf(right));
    }
}
