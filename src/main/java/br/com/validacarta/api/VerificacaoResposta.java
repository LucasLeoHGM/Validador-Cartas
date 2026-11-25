package br.com.validacarta.api;

import br.com.validacarta.dominio.CartaOficial;
import br.com.validacarta.dominio.DadosCarta;
import br.com.validacarta.dominio.EtapaPipeline;
import br.com.validacarta.dominio.ResultadoVerificacao;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Resposta da verificacao. textoExtraido e imagensTratadasBase64 so vem na hora da verificacao
 * (nao sao salvos); ao consultar pelo id, vem so o que esta no banco.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificacaoResposta(
        Long id,
        Instant criadoEm,
        String nomeArquivo,
        String interpretador,
        long duracaoMs,
        List<EtapaPipeline> etapas,
        String textoExtraido,
        Map<String, String> imagensTratadasBase64,
        DadosCarta dadosLidos,
        CartaOficial cartaOficial,
        String encontradaPor,
        ResultadoVerificacao resultado
) {
}
