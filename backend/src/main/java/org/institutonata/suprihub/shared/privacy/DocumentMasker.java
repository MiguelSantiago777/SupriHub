package org.institutonata.suprihub.shared.privacy;

import org.institutonata.suprihub.shared.validation.DocumentNumbers;

/**
 * Mascaramento de documentos para exibição pública (LGPD; Edital 6.2; Política art. 11).
 * <ul>
 *   <li>CNPJ: {@code 12.345.***}{@code /****-90}</li>
 *   <li>CPF: {@code ***.456.789-**} (padrão de anonimização do Governo Federal)</li>
 * </ul>
 */
public final class DocumentMasker {

    private DocumentMasker() {
    }

    public static String mask(String document) {
        String d = DocumentNumbers.digits(document);
        if (d.length() == 14) {
            return d.substring(0, 2) + "." + d.substring(2, 5) + ".***/****-" + d.substring(12);
        }
        if (d.length() == 11) {
            return "***." + d.substring(3, 6) + "." + d.substring(6, 9) + "-**";
        }
        return "***";
    }

    /** Mascara e-mail para logs/telas internas: {@code ma***@exemplo.com}. */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }
}
