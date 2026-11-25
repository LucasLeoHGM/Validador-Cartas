package br.com.validacarta.dominio;

/**
 * O que foi LIDO na foto da carta (o "JSON estruturado" da etapa de IA).
 * atk e def ficam como texto porque a carta pode ter "?" no lugar do numero.
 */
public record DadosCarta(
        String nome,
        String codigoSet,
        String idCarta,
        String atk,
        String def
) {
    public static DadosCarta vazio() {
        return new DadosCarta(null, null, null, null, null);
    }

    /** Nome, codigo do set ou passcode: qualquer um deles permite procurar a carta no banco oficial. */
    public boolean temIdentificador() {
        return preenchido(nome) || preenchido(codigoSet) || preenchido(idCarta);
    }

    public int camposLidos() {
        int n = 0;
        for (String s : new String[]{nome, codigoSet, idCarta, atk, def}) {
            if (preenchido(s)) n++;
        }
        return n;
    }

    private static boolean preenchido(String s) {
        return s != null && !s.isBlank();
    }
}
