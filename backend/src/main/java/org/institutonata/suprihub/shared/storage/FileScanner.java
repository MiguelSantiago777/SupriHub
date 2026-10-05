package org.institutonata.suprihub.shared.storage;

/**
 * Gancho para antivírus (fora do escopo atual). Uma implementação real (ex.: ClamAV)
 * basta ser registrada como bean para substituir a padrão, que não verifica nada.
 */
@FunctionalInterface
public interface FileScanner {

    ScanResult scan(String storageKey);

    enum ScanResult { CLEAN, INFECTED, NOT_SCANNED }
}
