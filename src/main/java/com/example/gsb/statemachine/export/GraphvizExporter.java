package com.example.gsb.statemachine.export;

import com.example.gsb.statemachine.StateMachineDefinition;

/**
 * 把状态机定义导出为 Graphviz DOT 文本。
 */
public final class GraphvizExporter {

    private GraphvizExporter() {
    }

    public static <S, E, C> String export(StateMachineDefinition<S, E, C> definition) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph ").append(definition.name()).append(" {\n");
        sb.append("    rankdir=LR;\n");
        sb.append("    __start [shape=point];\n");
        sb.append("    __start -> ").append(definition.initialState()).append(";\n");
        for (var t : definition.transitionViews()) {
            sb.append("    ").append(t.from())
                    .append(" -> ").append(t.to())
                    .append(" [label=\"").append(t.event());
            if (t.hasGuard()) {
                sb.append(" [guard]");
            }
            sb.append("\"];\n");
        }
        for (var t : definition.timeoutViews()) {
            sb.append("    ").append(t.state())
                    .append(" -> ").append(t.target())
                    .append(" [label=\"timeout(").append(t.timeout()).append(")\", style=dashed];\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
