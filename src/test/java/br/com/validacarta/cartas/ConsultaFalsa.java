package br.com.validacarta.cartas;

import br.com.validacarta.dominio.CartaOficial;
import br.com.validacarta.dominio.CodigoSet;
import br.com.validacarta.dominio.Texto;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Banco oficial de mentira, so com 2 cartas: os testes nao dependem de internet. */
public class ConsultaFalsa implements ConsultaCartas {

    public static final CartaOficial DARK_MAGICIAN = new CartaOficial(46986414L, "Dark Magician", "Normal Monster",
            2500, 2100, List.of(46986414L), List.of("LOB-EN005", "SDY-006"), null);
    public static final CartaOficial BLUE_EYES = new CartaOficial(89631139L, "Blue-Eyes White Dragon", "Normal Monster",
            3000, 2500, List.of(89631139L), List.of("LOB-EN001", "SDK-001"), null);

    private final List<CartaOficial> base = List.of(DARK_MAGICIAN, BLUE_EYES);
    public boolean foraDoAr;

    private void conectar() throws IOException {
        if (foraDoAr) throw new IOException("HTTP 503");
    }

    @Override
    public String nome() {
        return "falsa";
    }

    @Override
    public Optional<CartaOficial> porId(String id, String idioma) throws IOException {
        conectar();
        return base.stream().filter(c -> c.temId(Long.parseLong(id))).findFirst();
    }

    @Override
    public Optional<CartaOficial> porCodigoSet(String codigoSet, String idioma) throws IOException {
        conectar();
        Optional<CodigoSet> alvo = CodigoSet.ler(codigoSet);
        return base.stream().filter(c -> alvo.isPresent() && c.codigosSet().stream()
                .map(CodigoSet::ler).flatMap(Optional::stream).anyMatch(alvo.get()::mesmaCartaQue)).findFirst();
    }

    @Override
    public Optional<CartaOficial> porNome(String nome, String idioma) throws IOException {
        conectar();
        return base.stream().filter(c -> Texto.similaridade(c.nome(), nome) > 0.6)
                .max(Comparator.comparingDouble(c -> Texto.similaridade(c.nome(), nome)));
    }
}
