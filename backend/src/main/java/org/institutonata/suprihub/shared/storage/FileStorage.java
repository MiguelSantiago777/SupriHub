package org.institutonata.suprihub.shared.storage;

import java.io.InputStream;

/**
 * Armazenamento de arquivos enviados. A implementação atual grava em volume local
 * ({@link LocalFileStorage}); uma implementação S3 pode substituí-la sem mudar os módulos.
 */
public interface FileStorage {

    /**
     * Grava o conteúdo e devolve a chave de armazenamento (opaca) e o hash SHA-256.
     */
    StoredFile store(String folder, String originalFilename, InputStream content);

    InputStream load(String storageKey);

    void delete(String storageKey);

    record StoredFile(String storageKey, String sha256, long sizeBytes) {
    }
}
