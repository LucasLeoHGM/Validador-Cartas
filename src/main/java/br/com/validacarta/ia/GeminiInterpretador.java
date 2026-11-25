package br.com.validacarta.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Google Gemini (generateContent) pedindo resposta application/json. */
@Component
public class GeminiInterpretador extends ClienteLlm {

    private final String urlBase;

    public GeminiInterpretador(@Value("${app.ia.gemini.api-key:}") String apiKey,
                               @Value("${app.ia.gemini.modelo:gemini-2.5-flash}") String modelo,
                               @Value("${app.ia.gemini.url:https://generativelanguage.googleapis.com/v1beta/models}") String urlBase) {
        super(apiKey, modelo);
        this.urlBase = urlBase;
    }

    @Override
    public String nome() {
        return "gemini";
    }

    @Override
    protected String chamar(String sistema, String usuario) throws IOException {
        Map<String, Object> corpo = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", sistema))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", usuario)))),
                "generationConfig", Map.of("temperature", 0, "responseMimeType", "application/json"));
        // a chave vai no header (x-goog-api-key), nunca na URL, para nao aparecer em logs
        String resposta = post(urlBase + "/" + modelo + ":generateContent", Map.of("x-goog-api-key", apiKey), corpo);
        return json.readTree(resposta).path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
    }
}
