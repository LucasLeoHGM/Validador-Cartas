package br.com.validacarta.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Anthropic Claude (Messages API). O JSON vem no texto da resposta. */
@Component
public class ClaudeInterpretador extends ClienteLlm {

    private final String url;

    public ClaudeInterpretador(@Value("${app.ia.claude.api-key:}") String apiKey,
                               @Value("${app.ia.claude.modelo:claude-haiku-5-5}") String modelo,
                               @Value("${app.ia.claude.url:https://api.anthropic.com/v1/messages}") String url) {
        super(apiKey, modelo);
        this.url = url;
    }

    @Override
    public String nome() {
        return "claude";
    }

    @Override
    protected String chamar(String sistema, String usuario) throws IOException {
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "max_tokens", 1024,
                "temperature", 0,
                "system", sistema + "\nResponda somente com o JSON, sem texto antes ou depois.",
                "messages", List.of(Map.of("role", "user", "content", usuario)));
        String resposta = post(url, Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01"), corpo);
        return json.readTree(resposta).path("content").path(0).path("text").asText(null);
    }
}
