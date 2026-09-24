package com.example.gsb.statemachine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.gsb.statemachine.export.GraphvizExporter;
import com.example.gsb.statemachine.export.MermaidExporter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExporterTest {

    enum S { A, B, C }

    enum E { GO, BACK }

    private StateMachineDefinition<S, E, Void> definition() {
        return StateMachineBuilder.<S, E, Void>named("demo")
                .initialState(S.A)
                .transition().from(S.A).on(E.GO).to(S.B).guard(v -> true).add()
                .transition().from(S.B).on(E.BACK).to(S.A).add()
                .timeout(S.B, Duration.ofMinutes(5), S.C)
                .build();
    }

    @Test
    void mermaidExportContainsStatesEventsAndTimeout() {
        String mermaid = MermaidExporter.export(definition());

        assertThat(mermaid).contains("stateDiagram-v2");
        assertThat(mermaid).contains("[*] --> A");
        assertThat(mermaid).contains("A --> B : GO [guard]");
        assertThat(mermaid).contains("B --> A : BACK");
        assertThat(mermaid).contains("B --> C : timeout(PT5M)");
    }

    @Test
    void graphvizExportContainsStatesEventsAndTimeout() {
        String dot = GraphvizExporter.export(definition());

        assertThat(dot).contains("digraph demo");
        assertThat(dot).contains("__start -> A;");
        assertThat(dot).contains("A -> B [label=\"GO [guard]\"];");
        assertThat(dot).contains("B -> A [label=\"BACK\"];");
        assertThat(dot).contains("B -> C [label=\"timeout(PT5M)\", style=dashed];");
    }
}
