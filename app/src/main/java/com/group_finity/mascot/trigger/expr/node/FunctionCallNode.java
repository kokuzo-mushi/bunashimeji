package com.group_finity.mascot.trigger.expr.node;

import com.group_finity.mascot.trigger.expr.eval.EvaluationContext;
import com.group_finity.mascot.trigger.expr.eval.MascotFunction;
import com.group_finity.mascot.trigger.expr.type.TypeCoercion;
import com.group_finity.mascot.trigger.expr.type.TypeResolver;
import com.group_finity.mascot.trigger.expr.visitor.AstVisitor;
import java.util.ArrayList;
import java.util.List;

public class FunctionCallNode extends ExpressionNode {
    private final String functionName;
    private final List<ExpressionNode> arguments;

    public FunctionCallNode(String functionName, List<ExpressionNode> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<ExpressionNode> getArguments() {
        return arguments;
    }

    @Override
    public Object evaluate(EvaluationContext context, TypeResolver resolver, TypeCoercion coercion) {
        // 変数として関数オブジェクトを取得
        Object value = context.getVariable(functionName);
        
        if (value instanceof MascotFunction) {
            // 引数を評価
            List<Object> args = new ArrayList<>();
            for (ExpressionNode node : arguments) {
                args.add(node.evaluate(context, resolver, coercion));
            }
            // 関数実行
            return ((MascotFunction) value).apply(args);
        }
        
        throw new RuntimeException("Function not found or not callable: " + functionName);
    }

    @Override
    public void accept(AstVisitor visitor) {
        visitor.visit(this);
    }
}