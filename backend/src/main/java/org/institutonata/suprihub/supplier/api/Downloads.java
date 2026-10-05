package org.institutonata.suprihub.supplier.api;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.institutonata.suprihub.supplier.domain.Appeal;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Resposta de download de documento: tipo detectado no envio, sem cache e sem sniffing. */
final class Downloads {

    private Downloads() {
    }

    static ResponseEntity<InputStreamResource> of(SupplierDocument doc, InputStream content) {
        return of(doc.getOriginalFilename(), doc.getContentType(), doc.getSizeBytes(), content);
    }

    static ResponseEntity<InputStreamResource> of(Appeal.Attachment att, InputStream content) {
        return of(att.filename(), att.contentType(), att.sizeBytes(), content);
    }

    private static ResponseEntity<InputStreamResource> of(String filename, String contentType, long size,
            InputStream content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(size)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(content));
    }
}
