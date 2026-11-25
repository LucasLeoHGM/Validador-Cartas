package br.com.validacarta.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** OpenAI Chat Completions com resposta em JSON (response_format = json_object). */
@Component
public class OpenAiInterpretador extends ClienteLlm {

    private final String url;

    public OpenAiInterpretador(@Value("${app.ia.openai.api-key:}") String apiKey,
                               @Value("${app.ia.openai.modelo:gpt-4o-mini}") String modelo,
                               @Value("${app.ia.openai.url:https://api.openai.com/v1/chat/completions}") String url) {
        super(apiKey, modelo);
        this.url = url;
    }

    @Override
    public String nome() {
        return "openai";
    }

    @Override
    protected String chamar(String sistema, String usuario) throws IOException {
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", sistema),
                        Map.of("role", "user", "content", usuario)));
        String resposta = post(url, Map.of("Authorization", "Bearer " + apiKey), corpo);
        return json.readTree(resposta).path("choices").path(0).path("message").path("content").asText(null);
    }
}
