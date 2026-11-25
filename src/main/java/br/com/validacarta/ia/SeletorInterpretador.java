package br.com.validacarta.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Escolhe o interpretador conforme app.ia.provedor (regex | openai | gemini | claude).
 * Se o provedor escolhido nao tiver chave, usa o regex e avisa nas etapas do pipeline.
 */
@Component
public class SeletorInterpretador {

    private final Map<String, InterpretadorIa> porNome;
    private final String provedor;
    private final ExtratorCarta regex;

    public SeletorInterpretador(List<InterpretadorIa> interpretadores, ExtratorCarta regex,
                                @Value("${app.ia.provedor:regex}") String provedor) {
        this.porNome = interpretadores.stream().collect(Collectors.toMap(InterpretadorIa::nome, Function.identity()));
        this.regex = regex;
        this.provedor = provedor == null ? "regex" : provedor.trim().toLowerCase(Locale.ROOT);
    }

    public String provedorConfigurado() {
        return provedor;
    }

    /** O interpretador escolhido, se existir e estiver disponivel; senao, o regex. */
    public InterpretadorIa principal() {
        InterpretadorIa escolhido = porNome.get(provedor);
        return escolhido != null && escolhido.disponivel() ? escolhido : regex;
    }

    public ExtratorCarta fallback() {
        return regex;
    }

    /** Explica por que o provedor configurado nao esta sendo usado (ou null se esta tudo certo). */
    public String motivoIndisponivel() {
        InterpretadorIa escolhido = porNome.get(provedor);
        if (escolhido == null) return "Provedor '" + provedor + "' desconhecido";
        if (!escolhido.disponivel()) return "Chave de API do provedor '" + provedor + "' não configurada";
        return null;
    }
}
