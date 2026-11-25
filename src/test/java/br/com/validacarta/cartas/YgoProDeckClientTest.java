package br.com.validacarta.cartas;

import br.com.validacarta.dominio.CartaOficial;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testa a leitura do JSON no formato da API v7, sem acessar a internet. */
class YgoProDeckClientTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void leCartaDeMonstro() throws Exception {
        JsonNode data = json.readTree("""
                {"data":[{"id":46986414,"name":"Dark Magician","type":"Normal Monster","atk":2500,"def":2100,
                 "ygoprodeck_url":"https://ygoprodeck.com/card/dark-magician-4021",
                 "card_sets":[{"set_name":"A","set_code":"LOB-EN005"},{"set_name":"B","set_code":"LOB-EN005"}],
                 "card_images":[{"id":46986414},{"id":46986415}]}]}""");
        CartaOficial c = YgoProDeckClient.primeira(data).orElseThrow();
        assertEquals(46986414L, c.id());
        assertEquals(2500, c.atk());
        assertEquals(List.of("LOB-EN005"), c.codigosSet()); // sem repetir
        assertTrue(c.temId(46986415L));                       // arte alternativa
    }

    @Test
    void camposVaziosSaoOmitidosPelaApi() throws Exception {
        JsonNode data = json.readTree("{\"data\":[{\"id\":1,\"name\":\"Algum Link\",\"type\":\"Link Monster\",\"atk\":2300}]}");
        CartaOficial c = YgoProDeckClient.primeira(data).orElseThrow();
        assertNull(c.def());
        assertTrue(c.codigosSet().isEmpty());
    }

    @Test
    void respostaSemDadosECartaInexistente() throws Exception {
        assertTrue(YgoProDeckClient.primeira(json.readTree("{\"error\":\"No card matching your query\"}")).isEmpty());
    }

    @Test
    void buscaAproximadaEscolheONomeMaisParecido() throws Exception {
        JsonNode data = json.readTree("{\"data\":[{\"id\":2,\"name\":\"Dark Magician Girl\"},{\"id\":1,\"name\":\"Dark Magician\"}]}");
        Optional<CartaOficial> c = YgoProDeckClient.maisParecida(data, "Dark Maglclan");
        assertEquals(1L, c.orElseThrow().id());
    }

    @Test
    void buscaAproximadaSemNadaParecidoNaoChuta() throws Exception {
        JsonNode data = json.readTree("{\"data\":[{\"id\":2,\"name\":\"Blue-Eyes White Dragon\"}]}");
        assertTrue(YgoProDeckClient.maisParecida(data, "Zorblax").isEmpty());
    }
}
