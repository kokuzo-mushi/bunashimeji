package com.group_finity.mascot.trigger.expr.eval;

import java.util.List;

public interface MascotFunction {
    Object apply(List<Object> args);
}