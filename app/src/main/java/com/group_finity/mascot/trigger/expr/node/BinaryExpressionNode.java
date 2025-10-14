package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.type.CoercionPlan;

public final class BinaryExpressionNode implements ExpressionNode {

    private final String operator;
    private final ExpressionNode left;
    private final ExpressionNode right;
    private final CoercionPlan plan;
    private final Class<?> resultType;

    public BinaryExpressionNode(String operator, ExpressionNode left, ExpressionNode right) {
        this(operator, left, right, null, Object.class);
    }

    public BinaryExpressionNode(String operator,
                                ExpressionNode left,
                                ExpressionNode right,
                                CoercionPlan plan,
                                Class<?> resultType) {
        this.operator = operator;
        this.left = left;
        this.right = right;
        this.plan = plan;
        this.resultType = resultType != null ? resultType : Object.class;
    }

    @Override
    public Object evaluate(EvaluationContext ctx) {
        try {
            // short-circuit
            if ("&&".equals(operator)) {
                Object l = left.evaluate(ctx);
                boolean lb = ctx.toBoolean(l);
                if (!lb) return false;
                return ctx.toBoolean(right.evaluate(ctx));
            } else if ("||".equals(operator)) {
                Object l = left.evaluate(ctx);
                boolean lb = ctx.toBoolean(l);
                if (lb) return true;
                return ctx.toBoolean(right.evaluate(ctx));
            }

            Object lv = left.evaluate(ctx);
            Object rv = right.evaluate(ctx);

            // coercion plan if any
            Object lcv = lv;
            Object rcv = rv;
            if (plan != null) {
                try {
                    lcv = ctx.getTypeCoercion().coerceTo(lv, plan.leftTarget(), ctx.getMode());
                    rcv = ctx.getTypeCoercion().coerceTo(rv, plan.rightTarget(), ctx.getMode());
                } catch (Throwable t) {
                    // fallback gracefully
                    lcv = lv;
                    rcv = rv;
                }
            }

            // Debugging safe print
          /*  if (Boolean.TRUE.equals(ctx.getAttribute("debug")))*/ {
                System.out.printf("[ExprDebug] op=%s | left=%s(%s) | right=%s(%s)%n",
                        operator,
                        lcv, (lcv != null ? lcv.getClass().getSimpleName() : "null"),
                        rcv, (rcv != null ? rcv.getClass().getSimpleName() : "null"));
            }

            // ----- Operation handling -----
          switch (operator) {
          case "+":
              if (lcv instanceof String || rcv instanceof String)
                  return String.valueOf(lcv) + String.valueOf(rcv);
              
              // ★ 修正ロジック: 両方が整数型であれば Long を返す
              if (lcv instanceof Long && rcv instanceof Long) {
                  return ((Long)lcv).longValue() + ((Long)rcv).longValue();
              } else if (lcv instanceof Integer && rcv instanceof Integer) {
                  // Integer + Integer のケースも Long に昇格させておく
                  return ((Integer)lcv).longValue() + ((Integer)rcv).longValue();
              }
              
              // それ以外は Double に変換して計算
              return ctx.toNumber(lcv).doubleValue() + ctx.toNumber(rcv).doubleValue();

          case "-":
              // ★ 修正ロジック: 両方が整数型であれば Long を返す
              if (lcv instanceof Long && rcv instanceof Long) {
                  return ((Long)lcv).longValue() - ((Long)rcv).longValue();
              } else if (lcv instanceof Integer && rcv instanceof Integer) {
                  return ((Integer)lcv).longValue() - ((Integer)rcv).longValue();
              }
              
              // それ以外は Double に変換して計算
              return ctx.toNumber(lcv).doubleValue() - ctx.toNumber(rcv).doubleValue();
          
          case "*":
              // ★ 修正ロジック: 両方が整数型であれば Long を返す
              if (lcv instanceof Long && rcv instanceof Long) {
                  return ((Long)lcv).longValue() * ((Long)rcv).longValue();
              } else if (lcv instanceof Integer && rcv instanceof Integer) {
                  return ((Integer)lcv).longValue() * ((Integer)rcv).longValue();
              }
              
              // それ以外は Double に変換して計算
              return ctx.toNumber(lcv).doubleValue() * ctx.toNumber(rcv).doubleValue();

                case "/":
                    double divisor = ctx.toNumber(rcv).doubleValue();
                    if (divisor == 0) return Double.NaN;
                    return ctx.toNumber(lcv).doubleValue() / divisor;

                case "%":
                    return ctx.toNumber(lcv).doubleValue() % ctx.toNumber(rcv).doubleValue();

                case "<":
                    return ctx.toNumber(lcv).doubleValue() < ctx.toNumber(rcv).doubleValue();

                case ">":
                    return ctx.toNumber(lcv).doubleValue() > ctx.toNumber(rcv).doubleValue();

                case "<=":
                    return ctx.toNumber(lcv).doubleValue() <= ctx.toNumber(rcv).doubleValue();

                case ">=":
                    return ctx.toNumber(lcv).doubleValue() >= ctx.toNumber(rcv).doubleValue();

                case "==":
                    if (lcv == rcv) return true;
                    if (lcv == null || rcv == null) return false;
                    if (lcv instanceof Number && rcv instanceof Number)
                        return Double.compare(((Number) lcv).doubleValue(), ((Number) rcv).doubleValue()) == 0;
                    return String.valueOf(lcv).equals(String.valueOf(rcv));

                case "!=":
                    if (lcv == rcv) return false;
                    if (lcv == null || rcv == null) return true;
                    if (lcv instanceof Number && rcv instanceof Number)
                        return Double.compare(((Number) lcv).doubleValue(), ((Number) rcv).doubleValue()) != 0;
                    return !String.valueOf(lcv).equals(String.valueOf(rcv));

                    // ★★★ ここから追加 ★★★
                case "===": // 厳密等価 (Strict Equality)
                    if (lcv == rcv) return true;
                    if (lcv == null || rcv == null || lcv.getClass() != rcv.getClass()) return false;
                    
                    if (lcv instanceof Number && rcv instanceof Number) {
                        // lcv.getClass() == rcv.getClass() が保証されているため、
                        // Long 対 Long、Double 対 Double の比較になる
                        return lcv.equals(rcv); // <- Longのequalsは型と値を厳密に比較する
                    }
                    return lcv.equals(rcv);

                case "!==": // 厳密不等価 (Strict Inequality)
                    // '===' の結果を反転させる
                    if (lcv == rcv) return false;
                    if (lcv == null || rcv == null || lcv.getClass() != rcv.getClass()) return true;
                    
                    if (lcv instanceof Number && rcv instanceof Number) {
                        return !lcv.equals(rcv);
                    }
                    return !lcv.equals(rcv);
                    
                default:
                    throw new UnsupportedOperationException("Unsupported operator: " + operator);
            }
        } catch (Throwable ex) {
            // Never crash evaluator
            System.err.println("[ExprError] " + operator + " evaluation failed: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public Class<?> getResultType() {
        return resultType;
    }
}