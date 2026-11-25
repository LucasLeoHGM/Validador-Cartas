package br.com.validacarta.dominio;

import java.util.List;

/**
 * A carta como o YGOPRODeck a conhece.
 * atk/def sao nulos em magias, armadilhas e Link Monsters (a API omite campos vazios).
 */
public record CartaOficial(
        long id,
        String nome,
        String tipo,
        Integer atk,
        Integer def,
        List<Long> idsArte,
        List<String> codigosSet,
        String urlPagina
) {
    /** O passcode impresso na carta pode ser o da arte original ou de uma arte alternativa. */
    public boolean temId(long outro) {
        return id == outro || idsArte.contains(outro);
    }
}
