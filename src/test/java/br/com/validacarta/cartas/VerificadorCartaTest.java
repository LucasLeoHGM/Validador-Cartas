package br.com.validacarta.cartas;

import br.com.validacarta.dominio.DadosCarta;
import br.com.validacarta.dominio.StatusVerificacao;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerificadorCartaTest {

    private final ConsultaFalsa banco = new ConsultaFalsa();
    private final VerificadorCarta verificador = new VerificadorCarta(banco);

    private StatusVerificacao status(String nome, String set, String id, String atk, String def) throws IOException {
        String texto = String.join("\n", nome, set, id, "ATK/" + atk + " DEF/" + def);
        return verificador.verificar(new DadosCarta(nome, set, id, atk, def), texto).resultado().status();
    }

    @Test
    void cartaCoerenteEVerificada() throws Exception {
        assertEquals(StatusVerificacao.VERIFICADA, status("Dark Magician", "LOB-EN005", "46986414", "2500", "2100"));
    }

    @Test
    void atkDiferenteDoOficialEInconsistente() throws Exception {
        assertEquals(StatusVerificacao.INCONSISTENTE, status("Dark Magician", "LOB-EN005", "46986414", "9999", "2100"));
    }

    @Test
    void codigoDoSetDeOutraCartaEInconsistente() throws Exception {
        // LOB-EN001 existe, mas e do Blue-Eyes; difere de LOB-EN005 em 1 digito so
        assertEquals(StatusVerificacao.INCONSISTENTE, status("Dark Magician", "LOB-EN001", "46986414", "2500", "2100"));
    }

    @Test
    void passcodeDeOutraCartaEInconsistente() throws Exception {
        assertEquals(StatusVerificacao.INCONSISTENTE, status("Dark Magician", "LOB-EN005", "89631139", "2500", "2100"));
    }

    @Test
    void codigoParecidoQueNaoExisteEErroDeLeitura() throws Exception {
        assertEquals(StatusVerificacao.REVISAO_MANUAL, status("Dark Magician", "LOB-EN006", "46986414", "2500", "2100"));
    }

    @Test
    void passcodeQueNaoExisteNaoDerrubaSeORestoConfere() throws Exception {
        assertEquals(StatusVerificacao.VERIFICADA, status("Dark Magician", "LOB-EN005", "11111111", "2500", "2100"));
    }

    @Test
    void soONomeNaoBastaParaVerificar() throws Exception {
        var r = verificador.verificar(new DadosCarta("Dark Magician", null, null, null, null), "Dark Magician");
        assertEquals(StatusVerificacao.REVISAO_MANUAL, r.resultado().status());
    }

    @Test
    void cartaQueNaoExisteVaiParaRevisao() throws Exception {
        assertEquals(StatusVerificacao.REVISAO_MANUAL, status("Zorblax The Imaginary", "ZZZ-EN001", "12345678", "1000", "1000"));
    }

    @Test
    void nadaLegivelVaiParaRevisao() throws Exception {
        var r = verificador.verificar(DadosCarta.vazio(), "# ~~ 12");
        assertEquals(StatusVerificacao.REVISAO_MANUAL, r.resultado().status());
    }

    @Test
    void apiForaDoArPropagaOErro() {
        banco.foraDoAr = true;
        assertThrows(IOException.class, () -> status("Dark Magician", "LOB-EN005", "46986414", "2500", "2100"));
    }
}
