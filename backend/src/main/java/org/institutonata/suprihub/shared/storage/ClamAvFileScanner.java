package org.institutonata.suprihub.shared.storage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Antivírus via clamd (protocolo INSTREAM por TCP). Se o ClamAV estiver fora do ar, o arquivo
 * é aceito como NOT_SCANNED e o erro vai para o log: indisponibilidade do antivírus não
 * impede o fornecedor de enviar documentos com prazo.
 */
public class ClamAvFileScanner implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvFileScanner.class);
    private static final int CHUNK = 64 * 1024;

    private final FileStorage storage;
    private final String host;
    private final int port;
    private final int timeoutMillis;

    public ClamAvFileScanner(FileStorage storage, String host, int port, Duration timeout) {
        this.storage = storage;
        this.host = host;
        this.port = port;
        this.timeoutMillis = (int) timeout.toMillis();
    }

    @Override
    public ScanResult scan(String storageKey) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            try (InputStream in = storage.load(storageKey)) {
                byte[] buffer = new byte[CHUNK];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.writeInt(read);
                    out.write(buffer, 0, read);
                }
            }
            out.writeInt(0);
            out.flush();
            String reply = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
            return interpret(reply, storageKey);
        } catch (IOException e) {
            log.error("Antivírus indisponível ({}:{}); arquivo {} aceito sem verificação: {}", host, port,
                    storageKey, e.getMessage());
            return ScanResult.NOT_SCANNED;
        }
    }

    static ScanResult interpret(String reply, String storageKey) {
        String r = reply.replace("\0", "").trim();
        if (r.endsWith("OK")) {
            return ScanResult.CLEAN;
        }
        if (r.endsWith("FOUND")) {
            log.warn("Arquivo {} recusado pelo antivírus: {}", storageKey, r);
            return ScanResult.INFECTED;
        }
        log.error("Resposta inesperada do ClamAV para {}: {}", storageKey, r);
        return ScanResult.NOT_SCANNED;
    }
}
