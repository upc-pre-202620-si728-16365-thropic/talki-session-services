package com.thropic.talki.livecoach.domain.model;

public class ScenarioStrategy implements SessionModeStrategy {
    @Override public String getMode() { return "scenario"; }
    @Override public int maxDurationMinutes() { return 15; }
    @Override
    public String buildSystemPrompt(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank()) {
            // Sin escenario específico: rol genérico para que el modo funcione igual.
            return "Eres un personaje de un escenario de práctica de oratoria en un contexto "
                    + "peruano (por ejemplo, atención al cliente o una reunión de trabajo). "
                    + "Interpreta ese rol y conversa con el usuario para que practique.";
        }
        return "Eres el personaje del escenario peruano '" + scenarioId + "'. Sigue el rol definido en el catálogo.";
    }
}
