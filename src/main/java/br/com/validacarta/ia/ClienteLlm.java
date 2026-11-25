package br.com.validacarta.ia;

import br.com.validacarta.dominio.DadosCarta;
import br.com.validacarta.dominio.Texto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Base comum para OpenAI, Gemini e Claude. Cada provedor so implementa como montar a requisicao HTTP
 * e onde fica o texto da resposta; o prompt, a leitura do JSON e as protecoes ficam aqui.
 *
 * Protecao anti-alucinacao: a IA conhece muitas cartas de cor e poderia "completar" uma carta mal lida
 * ou falsificada com os dados verdadeiros. Por isso um valor devolvido so e aceito se aparece no texto do OCR.
 * Quem decide se a carta e autentica e o banco oficial (YGOPRODeck), nunca a IA.
 */
public abstract class ClienteLlm implements InterpretadorIa {

    static final String PROMPT_SISTEMA = """
            Voce le o texto de OCR de uma foto de carta de Yu-Gi-Oh! (TCG). O texto tem erros de leitura e linhas soltas.
            Retorne apenas um objeto JSON com as chaves: nome, codigoSet, idCarta, atk, def.
            - nome: nome da carta, copiado EXATAMENTE como esta no texto. Nao corrija nem traduza.
            - codigoSet: codigo do set impresso abaixo da arte, como LOB-EN005.
            - idCarta: numero de 8 digitos (passcode) impresso no canto inferior esquerdo.
            - atk e def: pontos de ataque e defesa como texto, por exemplo "2500". Use "?" se a carta mostra "?".
            Se nao encontrar um campo, use null. Nao invente informacoes e nao use o que voce sabe sobre a carta:
            use somente o que esta no texto.""";

    protected static final Duration TIMEOUT = Duration.ofSeconds(30);
    protected final ObjectMapper json = new ObjectMapper();
    protected final String apiKey;
    protected final String modelo;

    protected ClienteLlm(String apiKey, String modelo) {
        this.apiKey = apiKey;
        this.modelo = modelo;
    }

    @Override
    public boolean disponivel() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String modelo() {
        return modelo;
    }

    @Override
    public DadosCarta interpretar(String texto) throws IOException {
        String original = texto == null ? "" : texto;
        String resposta = chamar(PROMPT_SISTEMA, "TEXTO DO OCR:\n" + original);
        return converter(lerJson(resposta), original);
    }

    /** Envia o prompt ao provedor e devolve o texto gerado pelo modelo (que deve ser um JSON). */
    protected abstract String chamar(String sistema, String usuario) throws IOException;

    /** Converte o JSON da IA em DadosCarta aplicando a protecao anti-alucinacao. */
    DadosCarta converter(JsonNode campos, String textoOriginal) {
        return new DadosCarta(
                nomeConfirmado(campo(campos, "nome"), textoOriginal),
                confirmado(campo(campos, "codigoSet"), textoOriginal),
                confirmado(soDigitos(campo(campos, "idCarta")), textoOriginal),
                pontos(campo(campos, "atk"), textoOriginal),
                pontos(campo(campos, "def"), textoOriginal));
    }

    /** Aceita a resposta com ou sem ```json ... ```, pegando do primeiro { ao ultimo }. */
    JsonNode lerJson(String resposta) throws IOException {
        if (resposta == null) throw new IOException("Resposta vazia da IA");
        int ini = resposta.indexOf('{');
        int fim = resposta.lastIndexOf('}');
        if (ini < 0 || fim <= ini) throw new IOException("A IA nao devolveu um JSON");
        return json.readTree(resposta.substring(ini, fim + 1));
    }

    /** Valor devolvido pela IA so e aceito se aparece no texto do OCR (ignorando caixa, espacos e pontuacao). */
    static String confirmado(String valor, String texto) {
        return valor != null && Texto.contem(texto, valor) ? valor : null;
    }

    /**
     * O nome tem tolerancia: o OCR erra letras em fontes estilizadas, entao vale se alguma linha do texto
     * for parecida (75%) com o nome que a IA devolveu. Um nome que nao tem nada a ver com o texto e descartado,
     * o que impede a IA de "adivinhar" a carta pelo que ela sabe.
     */
    static String nomeConfirmado(String nome, String texto) {
        if (nome == null) return null;
        if (Texto.contem(texto, nome)) return nome;
        for (String linha : texto.split("\\R")) {
            if (Texto.similaridade(linha, nome) >= 0.75) return nome;
        }
        return null;
    }

    /** "2500" so vale se esse numero aparece no texto; "?" e aceito como esta. */
    private static String pontos(String valor, String texto) {
        if (valor == null) return null;
        if (valor.equals("?")) return "?";
        String digitos = soDigitos(valor);
        return digitos != null && texto.matches("(?s).*(?<!\\d)" + digitos + "(?!\\d).*") ? digitos : null;
    }

    private static String soDigitos(String valor) {
        if (valor == null) return null;
        String d = valor.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private static String campo(JsonNode campos, String chave) {
        JsonNode v = campos.path(chave);
        return v.isTextual() && !v.asText().isBlank() && !"null".equalsIgnoreCase(v.asText()) ? v.asText().trim() : null;
    }

    /** POST JSON generico com timeout; erro HTTP vira IOException (o pipeline cai no fallback regex). */
    protected String post(String url, Map<String, String> headers, Object corpo) throws IOException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        headers.forEach(req::header);
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException(nome() + " respondeu HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Chamada interrompida", e);
        }
    }
}
