package com.group_finity.mascot.trigger.expr;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;

/**
 * ExprEvaluator — フレキシブル版（リフレクション）
 *
 * 目的：
 * - ExpressionParser の具体的な API がどの形で来ても動くようにする（parse のシグネチャ違い等）。
 * - 同一式文字列は AST をキャッシュして再利用する。
 *
 * 実行時に次の順で呼び出しを試みます（見つかった方法を使う）：
 *  1) ExpressionParser.static parse(String, TypeResolver) などの静的メソッド
 *  2) new ExpressionParser(TokenStream, TypeResolver) → parser.parseExpression()
 *  3) new ExpressionParser(String, TypeResolver) → parser.parseExpression() など
 *  4) new ExpressionParser() → parser.parse(expression) / parser.parseExpression()
 *
 * 注意：
 * - 実行時にパーサが見つからない、または期待型を返さない場合は評価を false として安全に落とします。
 * - 将来的に明確な API が確定したら、リフレクションを外して直接呼ぶ実装に差し替えるのが望ましいです。
 */
public class ExprEvaluator {

    private static final String PARSER_CLASS = "com.group_finity.mascot.trigger.expr.parser.ExpressionParser";
    private static final String TYPE_RESOLVER_CLASS = "com.group_finity.mascot.trigger.expr.type.TypeResolver";
    private static final String DEFAULT_TYPE_RESOLVER_CLASS = "com.group_finity.mascot.trigger.expr.type.DefaultTypeResolver";
    private static final String EXPRESSION_NODE_CLASS = "com.group_finity.mascot.trigger.expr.node.ExpressionNode";

    private final Map<String, ExpressionNode> cache = new ConcurrentHashMap<>();

    // Reflection resolved handles (may be null if not found)
    private final Class<?> parserClass;
    private final Class<?> expressionNodeClass;
    private final Class<?> typeResolverClass;
    private final Object defaultTypeResolverInstance;

    private final Method staticParseStringResolver; // e.g. static parse(String, TypeResolver)
    private final Method instanceParseWithString;   // e.g. parser.parse(String)
    private final Method instanceParseNoArg;        // e.g. parser.parseExpression()
    private final Constructor<?> ctorWithStreamResolver; // (TokenStream, TypeResolver) or similar
    private final Constructor<?> ctorWithStringResolver; // (String, TypeResolver)
    private final Constructor<?> noArgCtor;

    public ExprEvaluator() {
        Class<?> pc = null;
        Class<?> enc = null;
        Class<?> trc = null;
        Object drInstance = null;

        Method staticParse = null;
        Method parseWithString = null;
        Method parseNoArg = null;

        Constructor<?> ctorStreamResolver = null;
        Constructor<?> ctorStringResolver = null;
        Constructor<?> ctorNoArg = null;

        try {
            pc = Class.forName(PARSER_CLASS);
        } catch (ClassNotFoundException e) {
            pc = null;
        }

        try {
            enc = Class.forName(EXPRESSION_NODE_CLASS);
        } catch (ClassNotFoundException e) {
            enc = null;
        }

        try {
            trc = Class.forName(TYPE_RESOLVER_CLASS);
        } catch (ClassNotFoundException e) {
            trc = null;
        }

        // try to instantiate DefaultTypeResolver if available
        if (trc != null) {
            try {
                Class<?> def = Class.forName(DEFAULT_TYPE_RESOLVER_CLASS);
                drInstance = def.getDeclaredConstructor().newInstance();
            } catch (Exception ignored) {
                drInstance = null;
            }
        }

        if (pc != null) {
            // 1) static parse(String, TypeResolver) or static parse(String)
            Method[] methods = pc.getMethods();
            for (Method m : methods) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0] == String.class) {
                        // parse(String, TypeResolver)
                        staticParse = m;
                        break;
                    } else if (params.length == 1 && params[0] == String.class) {
                        // parse(String)
                        staticParse = m;
                        // don't break; prefer parse(String, resolver) if present
                    }
                }
            }

            // 2) instance methods: parse(String) or parseExpression()
            try {
                parseWithString = pc.getMethod("parse", String.class);
            } catch (NoSuchMethodException ignored) {
                // try other common names
                try { parseWithString = pc.getMethod("parseExpression", String.class); } catch (NoSuchMethodException ignored2) { parseWithString = null; }
            }
            if (parseWithString == null) {
                // maybe parseExpression with no args will be available on instance
                try { parseNoArg = pc.getMethod("parseExpression"); } catch (NoSuchMethodException ignored) { parseNoArg = null; }
                if (parseNoArg == null) {
                    try { parseNoArg = pc.getMethod("toExpressionNode"); } catch (NoSuchMethodException ignored2) { parseNoArg = null; }
                }
            } else {
                // also check for no-arg parseExpression
                try { parseNoArg = pc.getMethod("parseExpression"); } catch (NoSuchMethodException ignored) { /* ignore */ }
            }

            // 3) constructors: (TokenStream, TypeResolver), (String, TypeResolver), (String), no-arg
            for (Constructor<?> c : pc.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 2) {
                    // heuristic: second param is a type resolver
                    if ((trc != null && params[1].isAssignableFrom(trc)) || params[1].getSimpleName().toLowerCase().contains("resolver")) {
                        if (params[0].getSimpleName().toLowerCase().contains("token") || params[0] == String.class) {
                            ctorStreamResolver = c; // covers (TokenStream, TypeResolver) or (String, TypeResolver)
                        }
                    }
                } else if (params.length == 1 && params[0] == String.class) {
                    ctorStringResolver = c;
                } else if (params.length == 0) {
                    ctorNoArg = c;
                }
            }
        }

        parserClass = pc;
        expressionNodeClass = enc;
        typeResolverClass = trc;
        defaultTypeResolverInstance = drInstance;

        staticParseStringResolver = staticParse;
        instanceParseWithString = parseWithString;
        instanceParseNoArg = parseNoArg;
        ctorWithStreamResolver = ctorStreamResolver;
        ctorWithStringResolver = ctorStringResolver;
        noArgCtor = ctorNoArg;
    }

    /**
     * Evaluate an expression in the given context.
     * Returns the evaluation result, or Boolean.FALSE on error / inability to parse.
     */
    public Object evaluate(String expression, EvaluationContext context) {
        if (expression == null || expression.isEmpty()) return Boolean.FALSE;

        try {
            ExpressionNode node = cache.computeIfAbsent(expression, expr -> createNode(expr));
            if (node == null) return Boolean.FALSE;
            return node.evaluate(context);
        } catch (Exception e) {
            System.err.println("[ExprEvaluator] evaluate error for \"" + expression + "\": " + e.getMessage());
            return Boolean.FALSE;
        }
    }

    /**
     * Try to produce an ExpressionNode for the expression via reflection.
     */
    private ExpressionNode createNode(String expression) {
        // if parser class not found, nothing we can do
        if (parserClass == null) {
            System.err.println("[ExprEvaluator] Parser class not found: " + PARSER_CLASS);
            return null;
        }

        try {
            // 1) static parse methods (prefer signature with TypeResolver if available)
            if (staticParseStringResolver != null) {
                try {
                    Object result;
                    Class<?>[] params = staticParseStringResolver.getParameterTypes();
                    if (params.length == 2 && defaultTypeResolverInstance != null) {
                        result = staticParseStringResolver.invoke(null, expression, defaultTypeResolverInstance);
                    } else {
                        result = staticParseStringResolver.invoke(null, expression);
                    }
                    if (isExpressionNode(result)) return (ExpressionNode) result;
                } catch (IllegalAccessException | InvocationTargetException ignored) { /* fall through */ }
            }

            // 2) constructor with (String/TokenStream, TypeResolver) or (String, TypeResolver)
            if (ctorWithStreamResolver != null) {
                try {
                    Constructor<?> c = ctorWithStreamResolver;
                    Class<?>[] params = c.getParameterTypes();
                    Object parserInstance;
                    if (params.length == 2 && params[0] == String.class) {
                        // (String, TypeResolver)
                        if (defaultTypeResolverInstance != null) {
                            parserInstance = c.newInstance(expression, defaultTypeResolverInstance);
                        } else {
                            // fall back to single-arg string constructor if available
                            parserInstance = c.newInstance(expression, null);
                        }
                    } else {
                        // unknown first param type (TokenStream?), try to find a tokenstream-like class and build it
                        Object tokenStream = tryMakeTokenStream(expression, params[0]);
                        if (defaultTypeResolverInstance != null) {
                            parserInstance = c.newInstance(tokenStream, defaultTypeResolverInstance);
                        } else {
                            parserInstance = c.newInstance(tokenStream, null);
                        }
                    }
                    // get parse method
                    Object maybeNode = tryInvokeParseOnInstance(parserInstance);
                    if (isExpressionNode(maybeNode)) return (ExpressionNode) maybeNode;
                } catch (Throwable ignored) { /* continue to other options */ }
            }

            // 3) constructor with (String)
            if (ctorWithStringResolver != null) {
                try {
                    Object parserInstance = ctorWithStringResolver.newInstance(expression);
                    Object maybeNode = tryInvokeParseOnInstance(parserInstance);
                    if (isExpressionNode(maybeNode)) return (ExpressionNode) maybeNode;
                } catch (Throwable ignored) { /* continue */ }
            }

            // 4) no-arg constructor + instance parse method
            if (noArgCtor != null) {
                try {
                    Object parserInstance = noArgCtor.newInstance();
                    // if instance parse(String) exists, use it
                    if (instanceParseWithString != null) {
                        Object res = instanceParseWithString.invoke(parserInstance, expression);
                        if (isExpressionNode(res)) return (ExpressionNode) res;
                    }
                    // if parseExpression() no-arg exists, call it
                    if (instanceParseNoArg != null) {
                        Object res = instanceParseNoArg.invoke(parserInstance);
                        if (isExpressionNode(res)) return (ExpressionNode) res;
                    }
                } catch (Throwable ignored) { /* fall through */ }
            }
        } catch (Throwable t) {
            System.err.println("[ExprEvaluator] createNode unexpected error: " + t.getMessage());
        }

        System.err.println("[ExprEvaluator] Failed to parse expression into node: " + expression);
        return null;
    }

    private boolean isExpressionNode(Object o) {
        if (o == null) return false;
        if (o instanceof ExpressionNode) return true;
        if (expressionNodeClass != null && expressionNodeClass.isInstance(o)) return true;
        return false;
    }

    /**
     * Try to invoke a parse method on a parser instance and return the result.
     */
    private Object tryInvokeParseOnInstance(Object parserInstance) {
        if (parserInstance == null) return null;
        try {
            // try parse(String)
            if (instanceParseWithString != null) {
                try {
                    Object res = instanceParseWithString.invoke(parserInstance, (String) null);
                    if (isExpressionNode(res)) return res;
                } catch (IllegalArgumentException iae) {
                    // some parse(String) expects non-null; try with actual expression via reflection earlier path
                } catch (InvocationTargetException | IllegalAccessException ignored) { }
            }
            // try parseExpression()
            if (instanceParseNoArg != null) {
                try {
                    Object res = instanceParseNoArg.invoke(parserInstance);
                    if (isExpressionNode(res)) return res;
                } catch (InvocationTargetException | IllegalAccessException ignored) { }
            }

            // try common alternatives
            try {
                Method m = parserInstance.getClass().getMethod("toExpressionNode");
                Object res = m.invoke(parserInstance);
                if (isExpressionNode(res)) return res;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) { }

            try {
                Method m = parserInstance.getClass().getMethod("build");
                Object res = m.invoke(parserInstance);
                if (isExpressionNode(res)) return res;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) { }

        } catch (Throwable ignored) { }
        return null;
    }

    /**
     * Attempt to create a TokenStream-like object for the parser constructor.
     * This is heuristic: it looks for a class with 'Token'/'Lexer'/'Stream' in name and tries common factory methods.
     */
    private Object tryMakeTokenStream(String expression, Class<?> expectedClass) {
        if (expectedClass == null) return null;
        try {
            String name = expectedClass.getName().toLowerCase();
            // if expected type is String, just return the string
            if (expectedClass == String.class) return expression;
            // try to find a static factory like TokenStreams.from(String) or TokenStream.of(String)
            try {
                // try common helper class names
                String helper1 = "com.group_finity.mascot.trigger.expr.parser.TokenStreams";
                Class<?> helper = Class.forName(helper1);
                try {
                    Method m = helper.getMethod("from", String.class);
                    Object ts = m.invoke(null, expression);
                    if (expectedClass.isInstance(ts)) return ts;
                } catch (NoSuchMethodException ignored) { }
            } catch (ClassNotFoundException ignored) { }

            // try expectedClass constructor with String
            try {
                Constructor<?> c = expectedClass.getConstructor(String.class);
                return c.newInstance(expression);
            } catch (NoSuchMethodException ignored) { }

        } catch (Throwable ignored) { }
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
