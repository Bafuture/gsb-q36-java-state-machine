package com.example.gsb.statemachine.export;

import com.example.gsb.statemachine.StateMachineDefinition;

/**
 * 把状态机定义导出为 Mermaid stateDiagram-v2 文本。
 */
public final class MermaidExporter {

    private MermaidExporter() {
    }

    public static <S, E, C> String export(StateMachineDefinition<S, E, C> definition) {
        StringBuilder sb = new StringBuilder();
        sb.append("stateDiagram-v2\n");
        sb.append("    [*] --> ").append(definition.initialState()).append('\n');
        for (var t : definition.transitionViews()) {
            sb.append("    ").append(t.from())
                    .append(" --> ").append(t.to())
                    .append(" : ").append(t.event());
            if (t.hasGuard()) {
                sb.append(" [guard]");
            }
            sb.append('\n');
        }
        for (var t : definition.timeoutViews()) {
            sb.append("    ").append(t.state())
                    .append(" --> ").append(t.target())
                    .append(" : timeout(").append(t.timeout()).append(")\n");
        }
        return sb.toString();
    }
}
