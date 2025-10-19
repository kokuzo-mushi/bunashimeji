package com.group_finity.mascot.trigger.expr;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeCoercion;
import com.group_finity.mascot.trigger.expr.type.DefaultTypeResolverFactory;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;
import com.group_finity.mascot.trigger.expr.type.TypeResolverFactory;

/**
 * ExpressionEngine (リフレクション対応)
 *
 * - 実行時に com.group_finity.mascot.trigger.expr.parser.ExpressionParser の利用可能な
 *   コンストラクタ／parseメソッドを探索して呼び出します。
 * - 既存の ExpressionNode#evaluate(EvaluationContext) をそのまま使います。
 *
 * ※ 実環境で問題がなければ、将来的にリフレクションを外して直接呼ぶ実装に置換してください。
 */
public class ExpressionEngine {

    private static final String PARSER_FQN = "com.group_finity.mascot.trigger.expr.parser.ExpressionParser";
    private static final String TOKENSTREAM_HELPER = "com.group_finity.mascot.trigger.expr.parser.TokenStreams"; // もしあれば利用
    private final TypeResolverFactory typeResolverFactory;
    private final TypeCoercion typeCoercion;

    public ExpressionEngine() {
        this(new DefaultTypeResolverFactory(), new DefaultTypeCoercion());
    }

    public ExpressionEngine(TypeResolverFactory resolverFactory, TypeCoercion coercion) {
        this.typeResolverFactory = Objects.requireNonNull(resolverFactory);
        this.typeCoercion = Objects.requireNonNull(coercion);
    }

    /**
     * 式をパースして ExpressionNode を返す（解析時に EvaluationContext を渡す必要がある実装にも対応）
     */
    public ExpressionNode parse(String expression, EvaluationContext context) {
        try {
            Class<?> parserClass = Class.forName(PARSER_FQN);
            TypeResolver resolver = typeResolverFactory.createResolver();

            // 1) try constructor (TokenStream, TypeResolver) or (String, TypeResolver)
            for (Constructor<?> c : parserClass.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                try {
                    if (params.length == 2) {
                        Object firstArg = null;
                        // if first param is String, pass expression
                        if (params[0] == String.class) {
                            firstArg = expression;
                        } else {
                            // try to build tokenstream-like instance for params[0]
                            firstArg = tryMakeTokenStream(expression, params[0]);
                        }
                        Object parserInst = c.newInstance(firstArg, resolver);
                        // try instance parse methods
                        Object node = tryInvokeParserInstance(parserInst, expression, context);
                        if (isExpressionNode(node)) return (ExpressionNode) node;
                    } else if (params.length == 1 && params[0] == String.class) {
                        // constructor (String)
                        Object parserInst = c.newInstance(expression);
                        Object node = tryInvokeParserInstance(parserInst, expression, context);
                        if (isExpressionNode(node)) return (ExpressionNode) node;
                    }
                } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException ignored) {
                    // try next ctor
                }
            }

            // 2) try static parse methods on parserClass: parse(String, TypeResolver) or parse(String)
            for (Method m : parserClass.getMethods()) {
                if ((m.getName().equals("parse") || m.getName().equals("parseExpression")) && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] p = m.getParameterTypes();
                    try {
                        Object res;
                        if (p.length == 2 && p[0] == String.class) {
                            res = m.invoke(null, expression, resolver);
                        } else if (p.length == 1 && p[0] == String.class) {
                            res = m.invoke(null, expression);
                        } else {
                            continue;
                        }
                        if (isExpressionNode(res)) return (ExpressionNode) res;
                    } catch (IllegalAccessException | InvocationTargetException ignored) { }
                }
            }

            // 3) try no-arg ctor + instance parse(expression) or parseExpression()
            try {
                Constructor<?> noArg = parserClass.getDeclaredConstructor();
                Object parserInst = noArg.newInstance();
                Object node = tryInvokeParserInstance(parserInst, expression, context);
                if (isExpressionNode(node)) return (ExpressionNode) node;
            } catch (NoSuchMethodException ignored) { /* none */ }

        } catch (ClassNotFoundException e) {
            System.err.println("[ExpressionEngine] parser class not found: " + PARSER_FQN);
        } catch (Throwable t) {
            System.err.println("[ExpressionEngine] unexpected error: " + t.getMessage());
        }

        System.err.println("[ExpressionEngine] Failed to parse expression: " + expression);
        return null;
    }

    /**
     * 式を評価して結果を返す（node が null の場合は false）
     */
    public Object evaluate(String expression, EvaluationContext context) {
        ExpressionNode node = parse(expression, context);
        if (node == null) return Boolean.FALSE;
        return node.evaluate(context);
    }

    /* ---------------- helpers ---------------- */

    private boolean isExpressionNode(Object o) {
        if (o == null) return false;
        if (o instanceof ExpressionNode) return true;
        // best-effort: try to match by method existence
        try {
            Method m = o.getClass().getMethod("evaluate", EvaluationContext.class);
            if (m != null) return true;
        } catch (NoSuchMethodException ignored) { }
        return false;
    }

    private Object tryInvokeParserInstance(Object parserInst, String expression, EvaluationContext context) {
        if (parserInst == null) return null;
        Class<?> pc = parserInst.getClass();

        // 1) try instance.parse(String, EvaluationContext)
        try {
            Method m = pc.getMethod("parse", String.class, EvaluationContext.class);
            return m.invoke(parserInst, expression, context);
        } catch (NoSuchMethodException ignored) { } catch (Exception ignored) { }

        // 2) try instance.parse(String)
        try {
            Method m = pc.getMethod("parse", String.class);
            return m.invoke(parserInst, expression);
        } catch (NoSuchMethodException ignored) { } catch (Exception ignored) { }

        // 3) try no-arg parseExpression()
        try {
            Method m = pc.getMethod("parseExpression");
            return m.invoke(parserInst);
        } catch (NoSuchMethodException ignored) { } catch (Exception ignored) { }

        // 4) try toExpressionNode / build
        try {
            Method m = pc.getMethod("toExpressionNode");
            return m.invoke(parserInst);
        } catch (NoSuchMethodException ignored) { } catch (Exception ignored) { }

        try {
            Method m = pc.getMethod("build");
            return m.invoke(parserInst);
        } catch (NoSuchMethodException ignored) { } catch (Exception ignored) { }

        return null;
    }

    /**
     * TokenStream 相当のオブジェクトを組み立てられるなら返す。見つからなければ null。
     * 探索方法：
     *  - TokenStreams.from(String) のようなヘルパーを探す
     *  - 期待型に String コンストラクタがあれば new ExpectedType(expression)
     */
    private Object tryMakeTokenStream(String expression, Class<?> expectedType) {
        if (expectedType == null) return null;

        // if expected is String, just return expression
        if (expectedType == String.class) return expression;

        // try TokenStreams helper
        try {
            Class<?> helper = Class.forName(TOKENSTREAM_HELPER);
            for (Method m : helper.getMethods()) {
                if (m.getName().equals("from") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == String.class) {
                    Object ts = m.invoke(null, expression);
                    if (expectedType.isInstance(ts)) return ts;
                }
            }
        } catch (ClassNotFoundException ignored) { } catch (Exception ignored) { }

        // try constructor expectedType(String)
        try {
            Constructor<?> c = expectedType.getDeclaredConstructor(String.class);
            return c.newInstance(expression);
        } catch (NoSuchMethodException ignored) { } catch (Exception ignored) { }

        return null;
    }

    /* ---------------- getters ---------------- */

    public TypeResolverFactory getTypeResolverFactory() {
        return typeResolverFactory;
    }

    public TypeCoercion getTypeCoercion() {
        return typeCoercion;
    }
}
