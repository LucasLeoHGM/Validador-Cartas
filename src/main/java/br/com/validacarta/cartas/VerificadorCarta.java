package br.com.validacarta.cartas;

import br.com.validacarta.dominio.CartaOficial;
import br.com.validacarta.dominio.CodigoSet;
import br.com.validacarta.dominio.DadosCarta;
import br.com.validacarta.dominio.ResultadoVerificacao;
import br.com.validacarta.dominio.StatusVerificacao;
import br.com.validacarta.dominio.Texto;
import br.com.validacarta.ia.ExtratorCarta;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Etapa 5: confere o que foi lido na foto com o banco oficial (YGOPRODeck).
 *
 * <p>Primeiro acha a carta oficial, nesta ordem: passcode, codigo do set, nome. Depois confere cada campo
 * lido contra ela. O status sai assim:
 * <ul>
 *   <li>INCONSISTENTE: um campo lido contradiz a carta oficial (codigo de outro set, ATK diferente,
 *       nome que e de outra carta).</li>
 *   <li>REVISAO_MANUAL: leitura incerta (nome duvidoso, codigo "parecido", poucos campos, nada encontrado).</li>
 *   <li>VERIFICADA: pelo menos 2 campos conferem e nenhum contradiz.</li>
 * </ul>
 *
 * <p>Limite importante: isto confere se os DADOS impressos fazem sentido entre si e existem na base oficial.
 * Nao detecta uma falsificacao fisica bem feita (papel, impressao, textura).
 */
@Service
public class VerificadorCarta {

    static final double NOME_CONFERE = 0.75;
    static final double NOME_DUVIDOSO = 0.5;
    static final double OUTRA_CARTA = 0.85;
    static final int CONFIRMACOES_MINIMAS = 2;

    private final ConsultaCartas consulta;

    public VerificadorCarta(ConsultaCartas consulta) {
        this.consulta = consulta;
    }

    public record Verificacao(CartaOficial carta, String encontradaPor, ResultadoVerificacao resultado) {
    }

    public Verificacao verificar(DadosCarta lido, String textoOcr) throws IOException {
        var lista = new Checklist();
        if (!lido.temIdentificador()) {
            lista.falha("Não foi possível ler a carta: nenhum nome, código do set ou passcode foi encontrado", false);
            return new Verificacao(null, null, lista.resultado());
        }

        Optional<CodigoSet> codigo = CodigoSet.ler(lido.codigoSet());
        String idioma = codigo.map(CodigoSet::idiomaApi).orElse(null);

        // 1. achar a carta oficial: passcode > codigo do set > nome
        CartaOficial carta = null;
        String por = null;
        if (lido.idCarta() != null) {
            carta = consulta.porId(lido.idCarta(), idioma).orElse(null);
            if (carta != null) por = "passcode";
            else lista.aviso("Passcode " + lido.idCarta() + " não existe no YGOPRODeck (possível erro de leitura)");
        }
        if (carta == null && codigo.isPresent()) {
            carta = consulta.porCodigoSet(codigo.get().texto(), idioma).orElse(null);
            if (carta != null) por = "código do set";
            else lista.aviso("Código do set " + lido.codigoSet() + " não encontrado no YGOPRODeck");
        }
        if (carta == null && lido.nome() != null) {
            carta = consulta.porNome(lido.nome(), idioma).orElse(null);
            if (carta != null) por = "nome";
            else lista.aviso("Nome \"" + lido.nome() + "\" não encontrado no YGOPRODeck");
        }
        if (carta == null) {
            lista.falha("Carta não encontrada no YGOPRODeck: pode ser erro de leitura ou uma carta que não existe", false);
            return new Verificacao(null, null, lista.resultado());
        }
        lista.ok("Carta encontrada no YGOPRODeck (por " + por + "): " + carta.nome());

        int confirmacoes = 0;
        confirmacoes += conferirPasscode(lista, lido, carta, por);
        confirmacoes += conferirCodigoSet(lista, lido, codigo, carta, por, idioma);
        confirmacoes += conferirNome(lista, lido, textoOcr, carta, idioma, por);
        confirmacoes += conferirPontos(lista, "ATK", lido.atk(), carta.atk(), carta.tipo());
        confirmacoes += conferirPontos(lista, "DEF", lido.def(), carta.def(), carta.tipo());

        if (!lista.temFalha()) {
            if (confirmacoes >= CONFIRMACOES_MINIMAS) {
                lista.ok(confirmacoes + " campos lidos conferem com o banco oficial");
            } else {
                lista.falha("Só " + confirmacoes + " campo(s) conferem: envie uma foto mais nítida para verificar com segurança", false);
            }
        }
        return new Verificacao(carta, por, lista.resultado());
    }

    private int conferirPasscode(Checklist lista, DadosCarta lido, CartaOficial carta, String por) {
        if (lido.idCarta() == null || !"passcode".equals(por)) return 0;
        lista.ok("Passcode " + lido.idCarta() + " confere");
        return 1;
    }

    private int conferirCodigoSet(Checklist lista, DadosCarta lido, Optional<CodigoSet> codigo, CartaOficial carta, String por,
                                  String idioma) throws IOException {
        if (lido.codigoSet() == null) return 0;
        if (codigo.isEmpty()) {
            lista.aviso("Código do set \"" + lido.codigoSet() + "\" em formato inesperado");
            return 0;
        }
        if ("código do set".equals(por)) {
            lista.ok("Código do set " + codigo.get().texto() + " confere");
            return 1;
        }
        List<CodigoSet> oficiais = carta.codigosSet().stream().map(CodigoSet::ler).flatMap(Optional::stream).toList();
        if (oficiais.isEmpty()) {
            lista.aviso("O YGOPRODeck não lista sets para esta carta: código do set não conferido");
            return 0;
        }
        if (oficiais.stream().anyMatch(codigo.get()::mesmaCartaQue)) {
            lista.ok("Código do set " + codigo.get().texto() + " pertence a esta carta");
            return 1;
        }
        // o codigo lido nao e desta carta. Se ele existe no banco e e de OUTRA carta, e contradicao clara
        // (ex.: LOB-EN001 e LOB-EN005 diferem em 1 digito, mas sao cartas diferentes)
        Optional<CartaOficial> dona = consulta.porCodigoSet(codigo.get().texto(), idioma);
        if (dona.isPresent() && dona.get().id() != carta.id()) {
            lista.falha("O código do set " + codigo.get().texto() + " pertence a outra carta (\"" + dona.get().nome()
                    + "\"), não a \"" + carta.nome() + "\". Se a leitura da foto estiver certa, a carta é inconsistente", true);
            return 0;
        }
        // nao existe no banco mas e parecido com um codigo desta carta: provavel erro de leitura do OCR
        if (oficiais.stream().anyMatch(codigo.get()::parecidoCom)) {
            lista.falha("Código do set " + codigo.get().texto() + " é parecido com um código oficial desta carta: confira a leitura", false);
            return 0;
        }
        lista.falha("O código do set " + codigo.get().texto() + " não pertence a esta carta (ex.: "
                + carta.codigosSet().stream().limit(4).toList() + ")", true);
        return 0;
    }

    private int conferirNome(Checklist lista, DadosCarta lido, String textoOcr, CartaOficial carta, String idioma, String por)
            throws IOException {
        List<String> nomesOficiais = new ArrayList<>(List.of(carta.nome()));
        List<String> lidos = new ArrayList<>(ExtratorCarta.candidatosDeNome(textoOcr == null ? "" : textoOcr));
        if (lido.nome() != null && !lidos.contains(lido.nome())) lidos.add(0, lido.nome());

        if (confere(nomesOficiais, lidos, textoOcr)) {
            lista.ok("Nome confere com \"" + carta.nome() + "\"");
            return 1;
        }
        // carta impressa em outro idioma: o nome da API veio traduzido, entao tenta tambem o nome em ingles
        if (idioma != null) {
            Optional<CartaOficial> ingles = consulta.porId(String.valueOf(carta.id()), null);
            if (ingles.isPresent()) {
                nomesOficiais.add(ingles.get().nome());
                if (confere(nomesOficiais, lidos, textoOcr)) {
                    lista.ok("Nome confere com \"" + ingles.get().nome() + "\"");
                    return 1;
                }
            }
        }
        if (lido.nome() == null) {
            lista.aviso("Nome não foi lido na foto");
            return 0;
        }
        double nota = melhorNota(nomesOficiais, lidos);
        if (nota >= NOME_DUVIDOSO) {
            lista.falha("Nome lido \"" + lido.nome() + "\" parece diferente de \"" + carta.nome() + "\" (" + Math.round(nota * 100) + "% parecido)", false);
            return 0;
        }
        // nada a ver com o nome oficial: se o nome lido for OUTRA carta de verdade, e uma contradicao clara
        if (!"nome".equals(por)) {
            Optional<CartaOficial> outra = consulta.porNome(lido.nome(), idioma);
            if (outra.isPresent() && outra.get().id() != carta.id()
                    && Texto.similaridade(outra.get().nome(), lido.nome()) >= OUTRA_CARTA) {
                lista.falha("O nome lido corresponde a outra carta (\"" + outra.get().nome() + "\"), mas o passcode/código é de \""
                        + carta.nome() + "\"", true);
                return 0;
            }
        }
        lista.falha("Não foi possível confirmar o nome (lido: \"" + lido.nome() + "\"): fontes decorativas dificultam o OCR", false);
        return 0;
    }

    private static boolean confere(List<String> oficiais, List<String> lidos, String textoOcr) {
        for (String oficial : oficiais) {
            // nomes curtissimos (ex.: "Ra") apareceriam em qualquer texto por acaso
            boolean longo = oficial.replaceAll("[^A-Za-z0-9]", "").length() >= 5;
            if (longo && textoOcr != null && Texto.contem(textoOcr, oficial)) return true;
        }
        return melhorNota(oficiais, lidos) >= NOME_CONFERE;
    }

    private static double melhorNota(List<String> oficiais, List<String> lidos) {
        double melhor = 0;
        for (String o : oficiais) {
            for (String l : lidos) melhor = Math.max(melhor, Texto.similaridade(o, l));
        }
        return melhor;
    }

    /** ATK/DEF so existem em monstros. "?" na carta nao e comparado (a API nao documenta como representa o "?"). */
    private int conferirPontos(Checklist lista, String rotulo, String lido, Integer oficial, String tipo) {
        if (lido == null || "?".equals(lido)) return 0;
        if (oficial == null || oficial < 0) {
            lista.aviso(rotulo + " " + lido + " foi lido, mas o banco oficial não tem " + rotulo + " para esta carta (" + tipo + ")");
            return 0;
        }
        if (lido.equals(String.valueOf(oficial))) {
            lista.ok(rotulo + " " + oficial + " confere");
            return 1;
        }
        lista.falha(rotulo + " lido " + lido + ", mas o oficial é " + oficial, true);
        return 0;
    }

    /** Junta as regras atendidas e as pendencias e decide o status. */
    private static final class Checklist {
        private final List<String> atendidas = new ArrayList<>();
        private final List<String> pendencias = new ArrayList<>();
        private boolean contradiz;
        private boolean incerta;

        void ok(String regra) {
            atendidas.add(regra);
        }

        /** Informacao que nao muda o status. */
        void aviso(String mensagem) {
            pendencias.add("Aviso: " + mensagem);
        }

        /** @param contradicao true = INCONSISTENTE; false = REVISAO_MANUAL */
        void falha(String mensagem, boolean contradicao) {
            pendencias.add(mensagem);
            if (contradicao) contradiz = true;
            else incerta = true;
        }

        boolean temFalha() {
            return contradiz || incerta;
        }

        ResultadoVerificacao resultado() {
            StatusVerificacao status = contradiz ? StatusVerificacao.INCONSISTENTE
                    : incerta ? StatusVerificacao.REVISAO_MANUAL
                    : StatusVerificacao.VERIFICADA;
            return new ResultadoVerificacao(status, List.copyOf(atendidas), List.copyOf(pendencias));
        }
    }
}
