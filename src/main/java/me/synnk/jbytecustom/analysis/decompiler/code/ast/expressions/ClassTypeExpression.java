package me.synnk.jbytecustom.analysis.decompiler.code.ast.expressions;

import me.synnk.jbytecustom.analysis.decompiler.code.ast.Expression;

public class ClassTypeExpression extends Expression {

    private String object;

    public ClassTypeExpression(String object) {
        this.object = object;
    }

    @Override
    public String toString() {
        return object + ".class";
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public Expression clone() {
        return new ClassTypeExpression(object);
    }
}
