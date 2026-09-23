package com.example.statemachine.export;

import com.example.statemachine.core.TimeoutConfig;
import com.example.statemachine.core.Transition;
import com.example.statemachine.dsl.StateMachineDefinition;

/**
 * Exports a {@link StateMachineDefinition} as a Mermaid stateDiagram-v2 text.
 * Guarded transitions and timeouts are rendered with Mermaid note syntax
 * ({@code event [guard] / action} style labels are not supported natively by
 * state diagrams, so guards/actions are appended to the label).
 */
public final class MermaidExporter {

    private MermaidExporter() {
    }

    public static <S, E> String export(StateMachineDefinition<S, E> definition) {
        StringBuilder sb = new StringBuilder("stateDiagram-v2\n");
        sb.append("    [*] --> ").append(id(definition.initialState())).append('\n');

        for (Transition<S, E> transition : definition.transitions()) {
            sb.append("    ")
                    .append(id(transition.source())).append(" --> ")
                    .append(id(transition.target()))
                    .append(" : ").append(label(transition))
                    .append('\n');
        }

        for (TimeoutConfig<S, E> timeout : definition.timeouts().values()) {
            sb.append("    ")
                    .append(id(timeout.state())).append(" --> ")
                    .append(id(timeout.targetState()))
                    .append(" : ").append(timeout.timeoutEvent())
                    .append(" (timeout after ").append(timeout.timeoutMillis()).append("ms)")
                    .append('\n');
        }

        sb.append("    note right of ").append(id(definition.initialState()))
                .append(" : initial state\n");
        return sb.toString();
    }

    private static <S, E> String label(Transition<S, E> transition) {
        StringBuilder label = new StringBuilder().append(transition.event());
        if (transition.isGuarded()) {
            label.append(" [guard]");
        }
        if (transition.action() != null) {
            label.append(" / action");
        }
        return label.toString();
    }

    private static String id(Object value) {
        String text = String.valueOf(value);
        if (text.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return text;
        }
        return "S_" + Math.abs(text.hashCode());
    }
}
