package org.institutonata.suprihub.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;

import org.institutonata.suprihub.shared.error.BusinessException;
import org.springframework.web.multipart.MultipartFile;

/** Conferência de arquivos enviados: tipo real pelo conteúdo e nome seguro. */
public final class UploadedFiles {

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private UploadedFiles() {
    }

    /**
     * Tipo real pelo conteúdo (não confia na extensão nem no Content-Type do navegador).
     * PDF, JPG e PNG sempre; Word (.docx) só quando {@code allowDocx}.
     */
    public static String detect(MultipartFile file, boolean allowDocx) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado", e);
        }
        if (read >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return PDF;
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return PNG;
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (allowDocx && read >= 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4
                && name.endsWith(".docx")) {
            return DOCX;
        }
        throw new BusinessException("FILE_TYPE_NOT_ALLOWED", allowDocx
                ? "Envie o arquivo em PDF, Word (.docx), JPG ou PNG."
                : "Envie o documento em PDF, JPG ou PNG.");
    }

    /** Só o nome do arquivo (sem pastas), sem caracteres de controle, até 150 caracteres. */
    public static String safeFilename(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|*?:]", "").trim();
        if (name.isEmpty()) {
            name = "documento";
        }
        return name.length() > 150 ? name.substring(name.length() - 150) : name;
    }
}
