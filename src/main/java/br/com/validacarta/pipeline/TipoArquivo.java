package br.com.validacarta.pipeline;

/** Tipo do arquivo pelos primeiros bytes (assinatura), e nao pela extensao ou Content-Type informados. */
public enum TipoArquivo {
    PDF, PNG, JPEG, TEXTO, DESCONHECIDO;

    public static TipoArquivo detectar(byte[] b) {
        if (b == null || b.length < 4) return b != null && b.length > 0 ? TEXTO : DESCONHECIDO;
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return PDF;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return PNG;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return JPEG;
        return pareceTexto(b) ? TEXTO : DESCONHECIDO;
    }

    public boolean imagemOuPdf() {
        return this == PDF || this == PNG || this == JPEG;
    }

    private static boolean pareceTexto(byte[] b) {
        int n = Math.min(b.length, 2048);
        for (int i = 0; i < n; i++) {
            int c = b[i] & 0xFF;
            if (c < 0x09 || (c > 0x0D && c < 0x20)) return false;
        }
        return true;
    }
}
