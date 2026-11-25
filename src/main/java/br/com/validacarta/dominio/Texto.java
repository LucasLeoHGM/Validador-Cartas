package br.com.validacarta.dominio;

import java.text.Normalizer;
import java.util.Locale;

public final class Texto {

    private Texto() {
    }

    /** "  João  da Silva" vira "JOAO DA SILVA": sem acento, maiusculo e com espacos simples. */
    public static String normalizar(String s) {
        if (s == null) return null;
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Compara ignorando acento, caixa, espacos e pontuacao. Serve para conferir se a IA nao inventou um valor. */
    public static boolean contem(String texto, String trecho) {
        if (texto == null || trecho == null) return false;
        String a = normalizar(texto).replaceAll("[^A-Z0-9]", "");
        String b = normalizar(trecho).replaceAll("[^A-Z0-9]", "");
        return !b.isEmpty() && a.contains(b);
    }

    /** Distancia de edicao (Levenshtein): quantas letras trocar, inserir ou apagar para igualar as duas. */
    public static int distancia(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] atual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) anterior[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            atual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int custo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                atual[j] = Math.min(Math.min(atual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + custo);
            }
            int[] troca = anterior;
            anterior = atual;
            atual = troca;
        }
        return anterior[b.length()];
    }

    /** 0.0 a 1.0, ignorando acento, caixa, espacos e pontuacao. 1.0 = nomes iguais. */
    public static double similaridade(String a, String b) {
        if (a == null || b == null) return 0;
        String x = normalizar(a).replaceAll("[^A-Z0-9]", "");
        String y = normalizar(b).replaceAll("[^A-Z0-9]", "");
        int maior = Math.max(x.length(), y.length());
        return maior == 0 ? 0 : 1.0 - (double) distancia(x, y) / maior;
    }

    public static String limpar(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }
}
