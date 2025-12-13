package com.group_finity.mascot.trigger.expr.eval;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.group_finity.mascot.trigger.expr.type.Mode;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;

/**
 * 評価時の変数・依存関係・型変換・モードを保持するコンテキスト（D-5 安定版）
 * - 参照共有/コピーの両コンストラクタを用意
 * - 依存トラッキング（markAccess/clearAccessLog/snapshotDependencies）
 * - スナップショットAPI（getVariablesSnapshot/snapshotImmutable）
 * - 互換API（getVariable/setValue）を提供
 */
public class EvaluationContext {

    // 変数表（基本は LinkedHashMap/参照共有も可能）
    private final Map<String, Object> variables;

    // 依存トラッキング用（読み取りアクセスしたキー集合）
    private final Set<String> accessedKeys = ConcurrentHashMap.newKeySet();

    // 型変換器とモード（null許容：既存コード互換）
    private final TypeCoercion typeCoercion;
    private final Mode mode;

    /** 互換：Map だけ渡されたケース（ShimejiApp から使用） */
    public EvaluationContext(Map<String, Object> vars) {
        this(vars, null, Mode.STRICT, false); // 既定は STRICT、コピー
    }

    /** 標準：コピーして保持（従来挙動） */
    public EvaluationContext(Map<String, Object> vars, TypeCoercion coercion, Mode mode) {
        this(vars, coercion, mode, false);
    }

    /** 拡張：参照共有を選択可能（shareVariables=true で外部Mapと同一参照） */
    public EvaluationContext(Map<String, Object> vars,
                             TypeCoercion coercion,
                             Mode mode,
                             boolean shareVariables) {
        this.typeCoercion = coercion;
        this.mode = (mode != null ? mode : Mode.STRICT);
        if (vars == null) {
            this.variables = new LinkedHashMap<>();
        } else if (shareVariables) {
            // 参照共有：外部で put した変更がそのまま見える
            this.variables = vars;
        } else {
            // コピー保持：外部変更の影響を受けない
            this.variables = new LinkedHashMap<>(vars);
        }
    }

    // ========= 基本アクセサ =========

    public Map<String, Object> getVariables() {
        return variables;
    }

    public TypeCoercion getTypeCoercion() {
        return typeCoercion;
    }

    public Mode getMode() {
        return mode;
    }

    // ========= 依存トラッキング =========

    /** 変数アクセスの記録（VariableNode などから呼ばれる） */
    public void markAccess(String name) {
        if (name != null) accessedKeys.add(name);
    }

    /** 依存アクセスログのクリア（再評価直前に呼ぶ） */
    public void clearAccessLog() {
        accessedKeys.clear();
    }

    /** 現時点でアクセスされたキーの値スナップショット（順序安定） */
    public Map<String, Object> snapshotDependencies() {
        return accessedKeys.stream().collect(Collectors.toMap(
            k -> k,
            k -> {
                // getVariableのロジックを再利用して値を取得するが、accessedKeysは変更しない
                // 1. そのままのキーで検索
                Object val = variables.get(k);
                if (val != null || variables.containsKey(k)) {
                    return val;
                }
                // 2. ドットが含まれる場合はネスト探索を試みる
                if (k != null && k.contains(".")) {
                    Object resolved = resolvePath(k);
                    if (resolved != null) return resolved;
                }
                // 3. 標準関数の検索
                return STANDARD_FUNCTIONS.get(k);
            },
            (a, b) -> a,
            LinkedHashMap::new
        ));
    }

    // ========= スナップショットAPI =========

    /** 現在の変数表のコピーを返す（STRICT 判定・ログ出力などに使用） */
    public Map<String, Object> getVariablesSnapshot() {
        return new LinkedHashMap<>(variables);
    }

    /**
     * Immutability を想定した簡易スナップショット。
     * 新しい EvaluationContext を生成し、変数表はコピーして埋め込む。
     * （EventDispatcher のワーカー渡し用）
     */
    public EvaluationContext snapshotImmutable() {
        return new EvaluationContext(new LinkedHashMap<>(variables), typeCoercion, mode, false);
    }

    // ========= 互換API（既存コード対応） =========

    /** 既存：VariableNode からの読み取りで使用される */
    public Object getVariable(String name) {
        // 読み取り時にも依存記録する
        if (name != null) accessedKeys.add(name);
        
        // 1. そのままのキーで検索
        Object val = variables.get(name);
        if (val != null || variables.containsKey(name)) {
            return val;
        }

        // 2. ドットが含まれる場合はネスト探索を試みる
        if (name != null && name.contains(".")) {
            Object resolved = resolvePath(name);
            if (resolved != null) return resolved;
        }

        // 3. 標準関数の検索
        return STANDARD_FUNCTIONS.get(name);
    }

    private Object resolvePath(String path) {
        Object current = variables;
        for (String part : path.split("\\.")) {
            if (current == null) return null;

            if (current instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) current;
                if (map.containsKey(part)) {
                    current = map.get(part);
                    continue;
                }
            }
            
            // Mapで見つからない、またはMapでない場合はリフレクションでプロパティ探索
            current = getProperty(current, part);
        }
        return current;
    }

    private Object getProperty(Object obj, String name) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        try {
            // 1. getFoo()
            return cls.getMethod("get" + cap).invoke(obj);
        } catch (Exception e1) {
            try {
                // 2. isFoo()
                return cls.getMethod("is" + cap).invoke(obj);
            } catch (Exception e2) {
                try {
                    // 3. public field
                    return cls.getField(name).get(obj);
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    /** 既存：Main からの setValue(String, int/obj) 呼び出しに対応 */
    public void setValue(String name, Object value) {
        variables.put(name, value);
    }

    // ========= 標準関数 =========

    private static final Map<String, MascotFunction> STANDARD_FUNCTIONS = new HashMap<>();
    static {
        STANDARD_FUNCTIONS.put("Math.abs", args -> Math.abs(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.acos", args -> Math.acos(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.asin", args -> Math.asin(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.atan", args -> Math.atan(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.ceil", args -> Math.ceil(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.cos", args -> Math.cos(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.floor", args -> Math.floor(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.log", args -> Math.log(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.max", args -> Math.max(toDouble(getArg(args, 0)), toDouble(getArg(args, 1))));
        STANDARD_FUNCTIONS.put("Math.min", args -> Math.min(toDouble(getArg(args, 0)), toDouble(getArg(args, 1))));
        STANDARD_FUNCTIONS.put("Math.pow", args -> Math.pow(toDouble(getArg(args, 0)), toDouble(getArg(args, 1))));
        STANDARD_FUNCTIONS.put("Math.random", args -> Math.random());
        STANDARD_FUNCTIONS.put("Math.round", args -> Math.round(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.sin", args -> Math.sin(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.sqrt", args -> Math.sqrt(toDouble(getArg(args, 0))));
        STANDARD_FUNCTIONS.put("Math.tan", args -> Math.tan(toDouble(getArg(args, 0))));
    }

    private static Object getArg(List<Object> args, int index) {
        if (args == null || index < 0 || index >= args.size()) return null;
        return args.get(index);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
