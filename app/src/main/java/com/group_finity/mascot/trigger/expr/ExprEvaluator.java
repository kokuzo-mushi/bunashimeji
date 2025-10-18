package com.group_finity.mascot.trigger.expr;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;

/**
 * ExprEvaluator (リフレクション対応)
 * - 内部で ExpressionParser の具体API を柔軟に探索して呼び出します。
 * - 式文字列 -> AST (ExpressionNode) をキャッシュして再利用します。
 */
public class ExprEvaluator {

    private final Map<String, ExpressionNode> cache = new ConcurrentHashMap<>();

    /** ExpressionParser クラス名（存在するクラスに合わせて変更可） */
    private static final String PARSER_CLASS_NAME = "com.group_finity.mascot.expr.ExpressionParser";

    // キャッシュ作成時に動的に探索して使うためのリフレクション参照を保存
    private final Class<?> parserClass;
    private final Method staticParseMethod;   // 例: public static ExpressionNode parse(String)
    private final Method instanceParseMethod; // 例: public ExpressionNode parse(String)
    private final Constructor<?> stringCtor;   // 例: new ExpressionParser(String)
    private final Method toNodeMethod;        // 例: instance.toExpressionNode()

    public ExprEvaluator() {
        Class<?> pc = null;
        Method sm = null;
        Method im = null;
        Constructor<?> ctor = null;
        Method tn = null;

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            pc = Class.forName(PARSER_CLASS_NAME, true, cl);

            // 1) 静的 parse(String) メソッドを探す
            try {
                sm = pc.getMethod("parse", String.class);
            } catch (NoSuchMethodException ignored) {
                // 見つからない場合は次を試す
            }

            // 2) インスタンスの parse系メソッドを探す（parse, parseExpression など）
            if (sm == null) {
                String[] tryNames = new String[] { "parse", "parseExpression", "toExpressionNode" };
                for (String name : tryNames) {
                    try {
                        Method m = pc.getMethod(name, String.class);
                        im = m;
                        break;
                    } catch (NoSuchMethodException ignored) { }
                }
            }

            // 3) String 引数を受け取るコンストラクタを探す
            try {
                ctor = pc.getConstructor(String.class);
                // その場合インスタンス生成後に AST 取得用メソッドを探す (例: toExpressionNode, build, getNode)
                String[] outNames = new String[] { "toExpressionNode", "build", "getNode", "getExpressionNode" };
                for (String name : outNames) {
                    try {
                        tn = pc.getMethod(name);
                        break;
                    } catch (NoSuchMethodException ignored) { }
                }
                // もし tn が null のままでも、コンストラクタが直接 ExpressionNode を返すケースは稀なので次へ
            } catch (NoSuchMethodException ignored) {
                ctor = null;
            }

        } catch (ClassNotFoundException e) {
            // パーサークラス自体がプロジェクト内にない場合もあり得る
            pc = null;
        }

        parserClass = pc;
        staticParseMethod = sm;
        instanceParseMethod = im;
        stringCtor = ctor;
        toNodeMethod = tn;
    }

    /**
     * 式を評価する。
     */
    public Object evaluate(String expression, EvaluationContext context) {
        if (expression == null || expression.isEmpty()) {
            return false;
        }
        try {
            ExpressionNode node = cache.computeIfAbsent(expression, expr -> createNodeFromParser(expr));
            if (node == null) {
                // パーサーが見つからない／生成できない場合は常に false とする（安全側）
                return false;
            }
            return node.evaluate(context);
        } catch (RuntimeException re) {
            System.err.println("[ExprEvaluator] Runtime error evaluating \"" + expression + "\": " + re.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("[ExprEvaluator] Error evaluating \"" + expression + "\": " + e.getMessage());
            return false;
        }
    }

    /**
     * キャッシュ用に ExpressionNode を生成する（リフレクションで ExpressionParser を呼び出す）
     */
    private ExpressionNode createNodeFromParser(String expression) {
        try {
            // パーサークラス自体が無ければ null を返す（caller 側で false 扱い）
            if (parserClass == null) {
                System.err.println("[ExprEvaluator] Parser class not found: " + PARSER_CLASS_NAME);
                return null;
            }

            // 1) static parse(String) があれば使う
            if (staticParseMethod != null) {
                Object res = staticParseMethod.invoke(null, expression);
                if (res instanceof ExpressionNode) return (ExpressionNode) res;
                // もし違う型なら try to convert? ここでは instanceof チェックのみ
            }

            // 2) instance parse(String) メソッドがあればインスタンスを作って呼ぶ
            if (instanceParseMethod != null) {
                Object parserInstance = parserClass.getDeclaredConstructor().newInstance();
                Object res = instanceParseMethod.invoke(parserInstance, expression);
                if (res instanceof ExpressionNode) return (ExpressionNode) res;
            }

            // 3) String コンストラクタがあれば生成し、toNodeMethod を呼ぶパターン
            if (stringCtor != null) {
                Object parserInstance = stringCtor.newInstance(expression);
                if (toNodeMethod != null) {
                    Object res = toNodeMethod.invoke(parserInstance);
                    if (res instanceof ExpressionNode) return (ExpressionNode) res;
                } else if (parserInstance instanceof ExpressionNode) {
                    // もしコンストラクタが直接 ExpressionNode を返す珍しいケース
                    return (ExpressionNode) parserInstance;
                }
            }

            // 4) 最後の手段：no-arg ctor + toNodeMethod(without args)
            try {
                Object parserInstance = parserClass.getDeclaredConstructor().newInstance();
                if (toNodeMethod != null) {
                    Object res = toNodeMethod.invoke(parserInstance);
                    if (res instanceof ExpressionNode) return (ExpressionNode) res;
                }
            } catch (NoSuchMethodException ignored) { }

        } catch (ReflectiveOperationException roe) {
            System.err.println("[ExprEvaluator] Reflection error while creating ExpressionNode: " + roe.getMessage());
        }

        // ここまで来たら生成できなかった
        System.err.println("[ExprEvaluator] Failed to create ExpressionNode for: " + expression);
        return null;
    }

    public void clearCache() {
        cache.clear();
    }

    public ExpressionNode getCachedNode(String expression) {
        return cache.get(expression);
    }

    public int getCacheSize() {
        return cache.size();
    }
}
