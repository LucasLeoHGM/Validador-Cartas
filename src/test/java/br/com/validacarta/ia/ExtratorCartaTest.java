package br.com.validacarta.ia;

import br.com.validacarta.dominio.DadosCarta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExtratorCartaTest {

    private final ExtratorCarta extrator = new ExtratorCarta();

    @Test
    void leTodosOsCampos() {
        DadosCarta d = extrator.interpretar("Dark Magician\n[Spellcaster/Normal]\n(descricao)\nLOB-EN005\nATK/2500 DEF/2100\n46986414");
        assertEquals("Dark Magician", d.nome());
        assertEquals("LOB-EN005", d.codigoSet());
        assertEquals("46986414", d.idCarta());
        assertEquals("2500", d.atk());
        assertEquals("2100", d.def());
    }

    @Test
    void corrigeLetrasTrocadasPorNumerosNoOcr() {
        DadosCarta d = extrator.interpretar("LOB-EN0O5\nATK/2S00 DEF/21OO\n4698641A");
        assertEquals("LOB-EN005", d.codigoSet());
        assertEquals("2500", d.atk());
        assertEquals("2100", d.def());
        assertNull(d.idCarta()); // 7 digitos e uma letra: nao e um passcode
    }

    @Test
    void ignoraLixoDepoisDoCodigoDoSet() {
        // leitura real do Tesseract numa foto de teste
        assertEquals("LOB-EN005", extrator.interpretar("LOB-ENOO5S\nLOB-ENOO5").codigoSet());
    }

    @Test
    void interrogacaoContinuaInterrogacao() {
        DadosCarta d = extrator.interpretar("Blue-Eyes White Dragon\nATK/ ? DEF/ ?");
        assertEquals("?", d.atk());
        assertEquals("Blue-Eyes White Dragon", d.nome());
    }

    @Test
    void votacaoEntreLeiturasRepetidas() {
        // o texto traz a mesma carta lida em varias imagens; o valor que mais se repete vence
        DadosCarta d = extrator.interpretar("LOB-EN005\nLOB-EN006\nLOB-EN005");
        assertEquals("LOB-EN005", d.codigoSet());
    }
}
