package br.com.validacarta.dominio;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Codigo impresso abaixo da arte, como LOB-EN005 (set LOB, regiao/idioma EN, numero 005).
 * Cartas antigas nao tem regiao (LOB-005) e algumas tem letra no numero (LDK2-ENK01).
 */
public record CodigoSet(String prefixo, String regiao, String numero) {

    private static final Pattern FORMATO = Pattern.compile("^([A-Z0-9]{2,6})-([A-Z]{0,3})([A-Z]?\\d{2,3})$");

    public static Optional<CodigoSet> ler(String bruto) {
        if (bruto == null) return Optional.empty();
        String limpo = bruto.toUpperCase().replaceAll("[\\s]", "").replace('–', '-').replace('—', '-');
        Matcher m = FORMATO.matcher(limpo);
        return m.matches() ? Optional.of(new CodigoSet(m.group(1), m.group(2), m.group(3))) : Optional.empty();
    }

    /** O mesmo lugar no mesmo set, nao importa o idioma da impressao (LOB-EN005 e LOB-PT005 sao a mesma carta). */
    public boolean mesmaCartaQue(CodigoSet outro) {
        return prefixo.equals(outro.prefixo) && numero.equals(outro.numero);
    }

    /** Mesmo set e numero parecido (1 caractere de diferenca): provavel erro de leitura do OCR. */
    public boolean parecidoCom(CodigoSet outro) {
        return prefixo.equals(outro.prefixo) && Texto.distancia(numero, outro.numero) <= 1;
    }

    /** Idioma aceito pelo parametro language da API (pt, fr, de, it) ou null para ingles. */
    public String idiomaApi() {
        return switch (regiao) {
            case "PT" -> "pt";
            case "FR", "FC" -> "fr";
            case "DE" -> "de";
            case "IT" -> "it";
            default -> null;
        };
    }

    public String texto() {
        return prefixo + "-" + regiao + numero;
    }
}
