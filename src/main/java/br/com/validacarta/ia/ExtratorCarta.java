package br.com.validacarta.ia;

import br.com.validacarta.dominio.DadosCarta;
import br.com.validacarta.dominio.Texto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracao SEM IA: regex para o que tem formato fixo (codigo do set, passcode, ATK/DEF) e
 * uma heuristica para o nome (primeira linha so com letras). Tambem e o fallback quando a IA falha.
 *
 * O texto chega com varias leituras da mesma carta (cabecalho, carta inteira, rodape...), entao cada
 * campo usa votacao: vale o valor que mais se repete.
 */
@Component
public class ExtratorCarta implements InterpretadorIa {

    // LOB-EN005, SDY-006, LDK2-ENK01. A regiao e uma lista fixa (senao o "O" lido no lugar do zero seria
    // confundido com letra de regiao) e o numero aceita letras parecidas com digitos (O, I, l, S, B),
    // que o OCR troca em letra miuda. Sobra de lixo depois do numero (ex.: "LOB-ENOO5S") e ignorada.
    private static final Pattern CODIGO_SET = Pattern.compile(
            "(?<![A-Z0-9])([A-Z][A-Z0-9]{1,4})-(EN|PT|FR|FC|DE|IT|SP|JP|KR|AE|SC|TC)?([0-9OIlSB]{3}|[A-Z][0-9OIlSB]{2})(?!\\d)");
    private static final Pattern PASSCODE = Pattern.compile("(?<!\\d)(\\d{8})(?!\\d)");
    private static final Pattern ATK = Pattern.compile("AT[KQ]\\s*[/|I]?\\s*([0-9?OSIl]{1,5})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEF = Pattern.compile("DEF\\s*[/|I]?\\s*([0-9?OSIl]{1,5})", Pattern.CASE_INSENSITIVE);

    @Override
    public String nome() {
        return "regex";
    }

    @Override
    public boolean disponivel() {
        return true;
    }

    @Override
    public DadosCarta interpretar(String texto) {
        String t = texto == null ? "" : texto;
        List<String> candidatos = candidatosDeNome(t);
        return new DadosCarta(
                candidatos.isEmpty() ? null : candidatos.get(0),
                maisFrequente(t, CODIGO_SET, ExtratorCarta::codigoCorrigido),
                maisFrequente(t, PASSCODE, m -> m.group(1)),
                maisFrequente(t, ATK, ExtratorCarta::pontosCorrigidos),
                maisFrequente(t, DEF, ExtratorCarta::pontosCorrigidos));
    }

    /**
     * Linhas que podem ser o nome da carta, na ordem em que aparecem: so letras, de 3 a 45 caracteres,
     * sem palavras de rotulo (ATK, DEF, LEVEL) nem colchetes do tipo "[Spellcaster/Normal]".
     */
    public static List<String> candidatosDeNome(String texto) {
        List<String> candidatos = new ArrayList<>();
        for (String linha : texto.split("\\R")) {
            String l = linha.replaceAll("[|_~=*]+", " ").replaceAll("\\s+", " ").trim();
            if (l.length() < 3 || l.length() > 45) continue;
            if (l.chars().anyMatch(Character::isDigit)) continue;
            if (l.matches(".*[\\[\\]/].*")) continue;
            if (l.toUpperCase().matches(".*\\b(ATK|DEF|LEVEL|RANK|LINK|PENDULUM)\\b.*")) continue;
            long letras = l.chars().filter(Character::isLetter).count();
            if (letras < 3 || letras < l.length() * 0.7) continue;
            if (!candidatos.contains(l)) candidatos.add(l);
            if (candidatos.size() >= 6) break;
        }
        return candidatos;
    }

    private interface Leitor {
        String ler(Matcher m);
    }

    private static String maisFrequente(String texto, Pattern p, Leitor leitor) {
        Map<String, Integer> votos = new LinkedHashMap<>();
        Matcher m = p.matcher(texto);
        while (m.find()) {
            String v = leitor.ler(m);
            if (v != null) votos.merge(v, 1, Integer::sum);
        }
        String melhor = null;
        int max = 0;
        for (var e : votos.entrySet()) {
            if (e.getValue() > max) {
                melhor = e.getKey();
                max = e.getValue();
            }
        }
        return melhor;
    }

    private static String codigoCorrigido(Matcher m) {
        String regiao = m.group(2) == null ? "" : m.group(2);
        String numero = m.group(3);
        // "K01" (letra + 2 digitos) so corrige os digitos; "OO5" vira "005"
        String corrigido = numero.matches("[0-9OIlSB]{3}") ? digitos(numero) : numero.charAt(0) + digitos(numero.substring(1));
        return m.group(1) + "-" + regiao + corrigido;
    }

    /** ATK/2S00 vira 2500; "?" continua "?". */
    private static String pontosCorrigidos(Matcher m) {
        String v = m.group(1);
        return v.contains("?") ? "?" : digitos(v);
    }

    private static String digitos(String s) {
        return s.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1').replace('S', '5').replace('B', '8');
    }

    /** Usado pelo verificador: o texto do OCR contem o nome oficial? */
    public static boolean textoContemNome(String texto, String nomeOficial) {
        return Texto.contem(texto, nomeOficial);
    }
}
