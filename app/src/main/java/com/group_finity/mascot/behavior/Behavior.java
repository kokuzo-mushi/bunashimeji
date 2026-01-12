package com.group_finity.mascot.behavior;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.action.Action;
import com.group_finity.mascot.trigger.Trigger;
import com.group_finity.mascot.trigger.event.EventEnvelope;
import com.group_finity.mascot.trigger.event.EventType;
import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.script.ScriptEngineManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * マスコットの行動（ビヘイビア）を定義するクラス。
 * 
 * <p>
 * 従来のXML定義（条件式 + アクション参照）に加え、
 * GraalJSを使用したJavaScriptジェネレータによる非同期ビヘイビア定義をサポートします。
 * </p>
 */
public class Behavior implements Trigger {
    private static final Logger log = Logger.getLogger(Behavior.class.getName());

    private String name;
    private int frequency;
    private String condition; // Legacy JEXL condition
    private boolean hidden;
    private boolean enabled = true; // New field for Settings UI
    private String actionName; // Legacy Action Reference
    private Action action; // XML defined action prototype

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // GraalJS Support
    private Source scriptSource;

    public Behavior() {
    }

    public Behavior(String name, int frequency, String condition) {
        this.name = name;
        this.frequency = frequency;
        this.condition = condition;
    }

    public Behavior(String name, Action action, String condition, boolean hidden, int frequency) {
        this.name = name;
        this.action = action;
        this.condition = condition;
        this.hidden = hidden;
        this.frequency = frequency;
    }

    // --- Getters & Setters for XML Binding ---
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    // --- Scripting Support ---

    /**
     * JavaScriptファイルを読み込み、このビヘイビアに関連付けます。
     */
    public void loadScript(Path path) throws IOException {
        this.scriptSource = ScriptEngineManager.INSTANCE.loadScript(path);
    }

    /**
     * アクションをインスタンス化します。
     * JSスクリプトが設定されている場合は、ジェネレータ関数を実行してActionラッパーを返します。
     */
    public Action instantiateAction(Mascot mascot) {
        // 1. JSスクリプトがある場合 (Modern)
        if (scriptSource != null) {
            Context context = mascot.getJsContext();
            if (context == null) {
                log.warning("Mascot has no JS Context. Skipping script execution.");
                return null;
            }

            try {
                // スクリプトを評価 (キャッシュされているので高速)
                Value exports = context.eval(scriptSource);

                // スクリプトが関数そのものを返している場合 ( module.exports = function*()... )
                if (exports.canExecute()) {
                    Value generator = exports.execute(mascot);
                    return new JSGeneratorAction(generator);
                }
            } catch (Exception e) {
                log.severe("Failed to execute behavior script: " + name + " / " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 2. XML定義の場合 (Legacy)
        if (action != null) {
            // TODO: Actionがステートフルの場合、ここで複製(clone)を返す必要がある
            return action;
        }
        return null;
    }

    @Override
    public Set<EventType> getSubscribedEventTypes() {
        return Set.of(EventType.values());
    }

    @Override
    public boolean evaluate(EventEnvelope<?> event, EvaluationContext context) {
        if (check(event, context)) {
            Mascot mascot = (Mascot) context.getVariables().get("mascot");
            if (mascot != null) {
                execute(event, mascot);
                return true;
            }
        }
        return false;
    }

    private boolean check(EventEnvelope<?> event, EvaluationContext context) {
        if (!enabled) {
            return false;
        }
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        Mascot mascot = (Mascot) context.getVariables().get("mascot");
        if (mascot == null)
            return false;

        try {
            // GraalJS Context を使用して条件式を評価
            Context jsContext = mascot.getJsContext();
            if (jsContext != null) {
                // イベント変数を一時的に注入
                Value bindings = jsContext.getBindings("js");

                // EvaluationContext の変数（isOnEdge, mouse.x 等）を JS 側に同期
                for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
                    bindings.putMember(entry.getKey(), entry.getValue());
                }

                // 引数の event は EvaluationContext の変数よりも優先する（null上書き防止）
                bindings.putMember("event", event);

                Value result = jsContext.eval("js", condition);
                // 結果が関数である場合（例: "mascot.isGrounded"）、実行して真偽値を得る
                if (result.canExecute()) {
                    result = result.execute();
                }
                return result.asBoolean();
            }
            return false;
        } catch (Exception e) {
            log.warning("Condition evaluation failed: " + condition + " -> " + e.getMessage());
            // e.printStackTrace(); // 必要に応じて有効化
            return false;
        }
    }

    private void execute(EventEnvelope<?> event, Mascot mascot) {
        Action action = instantiateAction(mascot);
        if (action != null) {
            mascot.setNextAction(action);
            // デバッグログ: 挨拶イベントの発火を確認
            if (name != null && name.startsWith("Greet")) {
                System.out.println("[Behavior] Fired: " + name);
            }
        }
    }

    /**
     * JSジェネレータをActionインターフェースに適合させるアダプタ
     */
    private static class JSGeneratorAction implements Action {
        private final Value iterator;
        private boolean finished = false;

        public JSGeneratorAction(Value generatorOrIterator) {
            if (generatorOrIterator.hasMember("next")) {
                this.iterator = generatorOrIterator;
            } else if (generatorOrIterator.hasIterator()) {
                this.iterator = generatorOrIterator.getIterator();
            } else {
                // ジェネレータでない場合
                this.iterator = null;
                this.finished = true;
            }
        }

        @Override
        public void execute(Mascot mascot) {
            if (finished || iterator == null)
                return;

            try {
                // next() を呼び出す
                Value result = iterator.getMember("next").execute();
                if (result.getMember("done").asBoolean()) {
                    finished = true;
                } else {
                    // yield された値を取得 (必要に応じて待機時間などに使う)
                    // Value value = result.getMember("value");
                }
            } catch (Exception e) {
                e.printStackTrace();
                finished = true;
            }
        }

        @Override
        public boolean hasNext() {
            return !finished;
        }
    }
}