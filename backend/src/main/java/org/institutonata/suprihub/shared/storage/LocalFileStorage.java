package org.institutonata.suprihub.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.springframework.stereotype.Component;

/**
 * Armazena arquivos no sistema de arquivos local (volume Docker em produção).
 * O nome original nunca é usado no caminho — evita path traversal e vazamento de dados no nome.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(SupriHubProperties props) {
        this.root = Path.of(props.storage().localPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar o diretório de arquivos", e);
        }
    }

    @Override
    public StoredFile store(String folder, String originalFilename, InputStream content) {
        String safeFolder = folder.replaceAll("[^a-zA-Z0-9_-]", "");
        String key = safeFolder + "/" + UUID.randomUUID();
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (DigestInputStream in = new DigestInputStream(content, digest)) {
                size = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(key, HexFormat.of().formatHex(digest.digest()), size);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar arquivo", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Arquivo não encontrado", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao excluir arquivo", e);
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        return p;
    }
}
