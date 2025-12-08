package com.group_finity.mascot.trigger.expr.visitor;

import com.group_finity.mascot.trigger.expr.node.BinaryExpressionNode;
import com.group_finity.mascot.trigger.expr.node.LiteralNode;
import com.group_finity.mascot.trigger.expr.node.UnaryExpressionNode;
import com.group_finity.mascot.trigger.expr.node.VariableNode;

/**
 * ASTノードを巡回するためのVisitorインターフェース。
 * ダブルディスパッチのために使用される。
 */
public interface AstVisitor {
    void visit(VariableNode node);
    void visit(LiteralNode node);
    void visit(BinaryExpressionNode node);
    void visit(UnaryExpressionNode node);
}