package com.group_finity.mascot.trigger.expr.eval;

import java.util.LinkedHashMap;
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
            k -> variables.get(k),
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
        return variables.get(name);
    }

    /** 既存：Main からの setValue(String, int/obj) 呼び出しに対応 */
    public void setValue(String name, Object value) {
        variables.put(name, value);
    }
}
