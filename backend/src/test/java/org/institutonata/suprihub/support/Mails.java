package org.institutonata.suprihub.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

/** Utilitários para inspecionar e-mails capturados nos testes. */
public final class Mails {

    private Mails() {
    }

    public static String html(MimeMessage message) {
        try {
            message.saveChanges();
            String html = find(message, "text/html");
            if (html == null) {
                throw new IllegalStateException("E-mail sem parte HTML");
            }
            return html;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String to(MimeMessage message) {
        try {
            return message.getAllRecipients()[0].toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Extrai o parâmetro {@code token} do primeiro link que o contenha. */
    public static String token(MimeMessage message) {
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(html(message));
        if (!m.find()) {
            throw new IllegalStateException("Token não encontrado no e-mail");
        }
        return m.group(1);
    }

    private static String find(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return part.getContent().toString();
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart body = multipart.getBodyPart(i);
                String found = find(body, mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
