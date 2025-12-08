package com.group_finity.mascot.trigger.expr.visitor;

import com.group_finity.mascot.trigger.expr.node.BinaryExpressionNode;
import com.group_finity.mascot.trigger.expr.node.ExpressionNode;
import com.group_finity.mascot.trigger.expr.node.LiteralNode;
import com.group_finity.mascot.trigger.expr.node.UnaryExpressionNode;
import com.group_finity.mascot.trigger.expr.node.VariableNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ASTを走査して、使用されているすべての変数名を収集するVisitor。
 */
public class VariableCollectorVisitor implements AstVisitor {

    private final Set<String> collectedVariables = new HashSet<>();

    public Set<String> getCollectedVariables() {
        return Collections.unmodifiableSet(collectedVariables);
    }

    @Override
    public void visit(VariableNode node) {
        collectedVariables.add(node.getVariableName());
    }

    @Override
    public void visit(LiteralNode node) {
        // リテラルは変数を含まないので何もしない
    }

    @Override
    public void visit(BinaryExpressionNode node) {
        // 再帰的に左右の子ノードを走査
        node.getLeft().accept(this);
        node.getRight().accept(this);
    }

    @Override
    public void visit(UnaryExpressionNode node) {
        // 再帰的に子ノードを走査
        node.getOperand().accept(this);
    }
}