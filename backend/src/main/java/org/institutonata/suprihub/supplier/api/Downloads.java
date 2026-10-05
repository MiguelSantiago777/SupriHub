package org.institutonata.suprihub.supplier.api;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .contentLength(doc.getSizeBytes())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(doc.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(content));
    }
}
