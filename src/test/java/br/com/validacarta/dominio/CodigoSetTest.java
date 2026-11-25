package br.com.validacarta.dominio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodigoSetTest {

    private static CodigoSet set(String s) {
        return CodigoSet.ler(s).orElseThrow();
    }

    @Test
    void idiomaDaImpressaoNaoMudaACarta() {
        assertTrue(set("LOB-EN005").mesmaCartaQue(set("LOB-PT005")));
        assertFalse(set("LOB-EN005").mesmaCartaQue(set("LOB-EN001")));
    }

    @Test
    void numeroParecidoIndicaErroDeLeitura() {
        assertTrue(set("LOB-EN005").parecidoCom(set("LOB-EN006")));
        assertFalse(set("LOB-EN005").parecidoCom(set("SDY-006")));
    }

    @Test
    void idiomaParaAApi() {
        assertEquals("pt", set("LOB-PT005").idiomaApi());
        assertNull(set("LOB-EN005").idiomaApi());
    }

    @Test
    void aceitaFormatosAntigosENovos() {
        assertTrue(CodigoSet.ler("SDY-006").isPresent());
        assertTrue(CodigoSet.ler("LDK2-ENK01").isPresent());
        assertTrue(CodigoSet.ler("lixo").isEmpty());
    }

    @Test
    void similaridadeDeNomes() {
        assertTrue(Texto.similaridade("Dark Magician", "Dark Maglclan") >= 0.75);
        assertTrue(Texto.similaridade("Dark Magician", "Blue-Eyes White Dragon") < 0.5);
    }
}
