package org.institutonata.suprihub.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ClamAvFileScannerTest {

    @Test
    void interpretaRespostasDoClamd() {
        assertThat(ClamAvFileScanner.interpret("stream: OK\0", "k")).isEqualTo(FileScanner.ScanResult.CLEAN);
        assertThat(ClamAvFileScanner.interpret("stream: Eicar-Signature FOUND\0", "k"))
                .isEqualTo(FileScanner.ScanResult.INFECTED);
        assertThat(ClamAvFileScanner.interpret("INSTREAM size limit exceeded. ERROR", "k"))
                .isEqualTo(FileScanner.ScanResult.NOT_SCANNED);
    }

    @Test
    void semClamdOArquivoFicaComoNaoVerificado() {
        // porta 1 em localhost: conexão recusada
        ClamAvFileScanner scanner = new ClamAvFileScanner(null, "127.0.0.1", 1, Duration.ofSeconds(1));
        assertThat(scanner.scan("k")).isEqualTo(FileScanner.ScanResult.NOT_SCANNED);
    }
}
