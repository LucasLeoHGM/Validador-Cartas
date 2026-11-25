package br.com.validacarta.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executa um programa externo (tesseract, python) com argumentos em lista - nada e concatenado,
 * entao o nome do arquivo enviado nao consegue injetar comandos. Tem timeout e le stdout/stderr
 * em paralelo para o processo nao travar com o buffer cheio.
 */
final class ComandoExterno {

    private ComandoExterno() {
    }

    static String executar(List<String> comando, long timeoutSegundos) throws IOException {
        Process p = new ProcessBuilder(comando).start();
        CompletableFuture<byte[]> saida = ler(p.getInputStream());
        CompletableFuture<byte[]> erro = ler(p.getErrorStream());
        try {
            if (!p.waitFor(timeoutSegundos, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException(comando.get(0) + " excedeu " + timeoutSegundos + "s");
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Execucao interrompida", e);
        }
        if (p.exitValue() != 0) {
            String msg = new String(erro.join(), StandardCharsets.UTF_8).strip();
            if (msg.length() > 300) msg = msg.substring(msg.length() - 300);
            throw new IOException(comando.get(0) + " terminou com codigo " + p.exitValue() + ": " + msg);
        }
        return new String(saida.join(), StandardCharsets.UTF_8);
    }

    /** Verifica se o programa existe rodando "programa --version". */
    static boolean existe(String programa) {
        try {
            Process p = new ProcessBuilder(programa, "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static CompletableFuture<byte[]> ler(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
