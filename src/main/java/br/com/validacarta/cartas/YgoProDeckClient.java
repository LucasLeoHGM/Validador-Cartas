package br.com.validacarta.cartas;

import br.com.validacarta.dominio.CartaOficial;
import br.com.validacarta.dominio.Texto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente da API publica do YGOPRODeck (v7): https://ygoprodeck.com/api-guide/
 *
 * Cuidados com as regras da API:
 * - limite de 20 requisicoes por segundo (passar disso bloqueia o IP por 1 hora): aqui ha um intervalo minimo
 *   entre chamadas e um cache em memoria, entao a mesma carta nunca e consultada duas vezes;
 * - as imagens das cartas NAO sao usadas (a API proibe hotlink); so os dados de texto.
 */
@Component
public class YgoProDeckClient implements ConsultaCartas {

    private static final long INTERVALO_MIN_MS = 70; // ~14 requisicoes por segundo, abaixo do limite de 20
    private static final int MAX_CACHE = 2000;

    private final String urlBase;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();
    private final ConcurrentHashMap<String, Optional<CartaOficial>> cache = new ConcurrentHashMap<>();
    private long ultimaChamada;

    public YgoProDeckClient(@Value("${app.ygoprodeck.url:https://db.ygoprodeck.com/api/v7}") String urlBase,
                            @Value("${app.ygoprodeck.timeout-segundos:15}") long timeoutSegundos) {
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.timeout = Duration.ofSeconds(timeoutSegundos);
    }

    @Override
    public String nome() {
        return "ygoprodeck";
    }

    public String urlBase() {
        return urlBase;
    }

    @Override
    public Optional<CartaOficial> porId(String id, String idioma) throws IOException {
        if (id == null || !id.matches("\\d{1,10}")) return Optional.empty();
        return emCache("id:" + id + ":" + idioma, () -> primeira(buscar("cardinfo.php", "id", id, idioma)));
    }

    @Override
    public Optional<CartaOficial> porCodigoSet(String codigoSet, String idioma) throws IOException {
        if (codigoSet == null || codigoSet.isBlank()) return Optional.empty();
        return emCache("set:" + codigoSet + ":" + idioma, () -> {
            // cardsetsinfo devolve so a carta daquela impressao; com o id dela buscamos a carta completa
            JsonNode info = buscar("cardsetsinfo.php", "setcode", codigoSet, null);
            JsonNode raiz = info.has("data") ? info.path("data").path(0) : info;
            long id = raiz.path("id").asLong(0);
            return id == 0 ? Optional.empty() : porId(String.valueOf(id), idioma);
        });
    }

    @Override
    public Optional<CartaOficial> porNome(String nome, String idioma) throws IOException {
        if (nome == null || nome.isBlank()) return Optional.empty();
        return emCache("nome:" + Texto.normalizar(nome) + ":" + idioma, () -> {
            Optional<CartaOficial> exata = primeira(buscar("cardinfo.php", "name", nome, idioma));
            if (exata.isPresent()) return exata;
            return maisParecida(buscar("cardinfo.php", "fname", nome, idioma), nome);
        });
    }

    // ---------- interpretacao do JSON (estatico: da para testar sem internet) ----------

    /** A API devolve {"data":[...]}. Campos vazios sao omitidos (Link Monster nao tem def, magia nao tem atk). */
    public static CartaOficial paraCarta(JsonNode c) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode img : c.path("card_images")) ids.add(img.path("id").asLong());
        List<String> codigos = new ArrayList<>();
        for (JsonNode s : c.path("card_sets")) {
            String codigo = s.path("set_code").asText("");
            if (!codigo.isEmpty() && !codigos.contains(codigo)) codigos.add(codigo);
        }
        return new CartaOficial(
                c.path("id").asLong(),
                c.path("name").asText(""),
                c.path("type").asText(""),
                numero(c.path("atk")),
                numero(c.path("def")),
                List.copyOf(ids),
                List.copyOf(codigos),
                c.path("ygoprodeck_url").asText(null));
    }

    private static Integer numero(JsonNode n) {
        return n.isNumber() ? n.asInt() : null;
    }

    public static Optional<CartaOficial> primeira(JsonNode resposta) {
        JsonNode data = resposta.path("data");
        return data.isArray() && !data.isEmpty() ? Optional.of(paraCarta(data.get(0))) : Optional.empty();
    }

    /** Entre varios resultados da busca aproximada, fica o de nome mais parecido (minimo 60%). */
    public static Optional<CartaOficial> maisParecida(JsonNode resposta, String nomeLido) {
        CartaOficial melhor = null;
        double melhorNota = 0.6;
        for (JsonNode c : resposta.path("data")) {
            double nota = Texto.similaridade(c.path("name").asText(""), nomeLido);
            if (nota > melhorNota) {
                melhorNota = nota;
                melhor = paraCarta(c);
            }
        }
        return Optional.ofNullable(melhor);
    }

    // ---------- HTTP ----------

    @FunctionalInterface
    private interface Busca {
        Optional<CartaOficial> executar() throws IOException;
    }

    private Optional<CartaOficial> emCache(String chave, Busca busca) throws IOException {
        Optional<CartaOficial> guardada = cache.get(chave);
        if (guardada != null) return guardada;
        Optional<CartaOficial> nova = busca.executar();
        if (cache.size() < MAX_CACHE) cache.put(chave, nova);
        return nova;
    }

    /**
     * GET em endpoint?parametro=valor[&language=xx]. Nao achar a carta vem como HTTP 400 com {"error": ...}
     * na API v7, e isso vira um JSON vazio (carta nao existe). Qualquer outro erro (429, 5xx, timeout) e IOException.
     */
    private JsonNode buscar(String endpoint, String parametro, String valor, String idioma) throws IOException {
        String url = urlBase + "/" + endpoint + "?" + parametro + "=" + URLEncoder.encode(valor, StandardCharsets.UTF_8);
        if (idioma != null) url += "&language=" + idioma;
        aguardarIntervalo();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", "validador-cartas (projeto academico)")
                .GET().build();
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build()) {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status == 400 || status == 404) return json.createObjectNode();
            if (status == 429) throw new IOException("YGOPRODeck: limite de requisicoes atingido (HTTP 429)");
            if (status / 100 != 2) throw new IOException("YGOPRODeck respondeu HTTP " + status);
            return json.readTree(resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Consulta interrompida", e);
        }
    }

    private synchronized void aguardarIntervalo() throws IOException {
        long espera = ultimaChamada + INTERVALO_MIN_MS - System.currentTimeMillis();
        if (espera > 0) {
            try {
                Thread.sleep(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Consulta interrompida", e);
            }
        }
        ultimaChamada = System.currentTimeMillis();
    }
}
