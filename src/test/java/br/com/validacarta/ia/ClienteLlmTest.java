package br.com.validacarta.ia;

import br.com.validacarta.dominio.DadosCarta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClienteLlmTest {

    /** IA falsa: devolve uma resposta fixa, sem chamar API nenhuma. */
    static class IaFalsa extends ClienteLlm {
        final String resposta;

        IaFalsa(String resposta) {
            super("chave", "modelo-teste");
            this.resposta = resposta;
        }

        @Override
        public String nome() {
            return "falsa";
        }

        @Override
        protected String chamar(String sistema, String usuario) {
            return resposta;
        }
    }

    private static final String TEXTO = "Dark Magician\nLOB-EN005\nATK/2500 DEF/2100\n46986414";

    @Test
    void leJsonMesmoComCercasDeMarkdown() throws Exception {
        var ia = new IaFalsa("```json\n{\"nome\":\"Dark Magician\",\"codigoSet\":\"LOB-EN005\",\"idCarta\":\"46986414\","
                + "\"atk\":\"2500\",\"def\":\"2100\"}\n```");
        DadosCarta d = ia.interpretar(TEXTO);
        assertEquals("Dark Magician", d.nome());
        assertEquals("46986414", d.idCarta());
        assertEquals("2500", d.atk());
    }

    @Test
    void descartaValoresQueNaoEstaoNoTexto() throws Exception {
        // a IA "conhece" a carta e tenta completar o que a foto nao mostra: nao pode
        var ia = new IaFalsa("{\"nome\":\"Blue-Eyes White Dragon\",\"codigoSet\":\"LOB-EN005\",\"idCarta\":\"46986414\","
                + "\"atk\":\"3000\",\"def\":\"2100\"}");
        DadosCarta d = ia.interpretar(TEXTO);
        assertNull(d.nome());
        assertNull(d.atk());
        assertEquals("LOB-EN005", d.codigoSet());
        assertEquals("2100", d.def());
    }

    @Test
    void aceitaNomeComErroDeOcr() throws Exception {
        var ia = new IaFalsa("{\"nome\":\"Dark Magician\"}");
        assertEquals("Dark Magician", ia.interpretar("Dark Maglclan\nATK/?").nome());
    }
}
