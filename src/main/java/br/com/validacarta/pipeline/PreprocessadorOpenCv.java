package br.com.validacarta.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Etapa 2: trata a foto com OpenCV (scripts/preprocessar.py).
 *
 * O script acha a carta na foto, endireita (correcao de perspectiva) e gera 4 imagens para o OCR:
 * "cabecalho" (nome), "carta" (inteira), "rodape" (codigo do set, passcode, ATK/DEF) e "binaria"
 * (preto e branco). Cada parte da carta e lida separadamente porque tem fonte e fundo diferentes.
 */
@Service
public class PreprocessadorOpenCv {

    private final boolean habilitado;
    private final String comando;
    private final long timeoutSegundos;

    public PreprocessadorOpenCv(@Value("${app.opencv.habilitado:true}") boolean habilitado,
                                @Value("${app.opencv.comando:python3 scripts/preprocessar.py}") String comando,
                                @Value("${app.opencv.timeout-segundos:60}") long timeoutSegundos) {
        this.habilitado = habilitado;
        this.comando = comando;
        this.timeoutSegundos = timeoutSegundos;
    }

    public boolean habilitado() {
        return habilitado && comando != null && !comando.isBlank();
    }

    /** imagens: nome da variante -> PNG (na ordem em que devem ser lidas); resumo: JSON impresso pelo script. */
    public record Resultado(Map<String, byte[]> imagens, String resumo) {
    }

    private static final List<String> VARIANTES = List.of("cabecalho", "carta", "rodape", "binaria");

    public Resultado processar(byte[] arquivo, TipoArquivo tipo) throws IOException {
        Path pasta = Files.createTempDirectory("validacarta-opencv-");
        try {
            Path entrada = pasta.resolve("entrada." + tipo.name().toLowerCase());
            Files.write(entrada, arquivo);
            List<String> cmd = new ArrayList<>(Arrays.asList(comando.trim().split("\\s+")));
            cmd.add(entrada.toString());
            cmd.add(pasta.resolve("saida").toString());
            String resumo = ComandoExterno.executar(cmd, timeoutSegundos).strip();

            Map<String, byte[]> imagens = new LinkedHashMap<>();
            for (String variante : VARIANTES) {
                Path png = pasta.resolve("saida-" + variante + ".png");
                if (Files.exists(png)) imagens.put(variante, Files.readAllBytes(png));
            }
            if (imagens.isEmpty()) throw new IOException("O script do OpenCV nao gerou imagem");
            return new Resultado(imagens, resumo);
        } finally {
            try (Stream<Path> arquivos = Files.list(pasta)) {
                for (Path p : arquivos.toList()) Files.deleteIfExists(p);
            }
            Files.deleteIfExists(pasta);
        }
    }
}
