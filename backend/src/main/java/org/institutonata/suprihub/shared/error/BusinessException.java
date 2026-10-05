package org.institutonata.suprihub.shared.error;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Violação de regra de negócio. A mensagem é exibida ao usuário (pt-BR) e o
 * código é estável para o frontend tratar casos específicos.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<Map<String, String>> errors;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        this(code, message, status, List.of());
    }

    /** Com pendências por campo ({@code field}, {@code message}), como nos erros de validação. */
    public BusinessException(String code, String message, HttpStatus status, List<Map<String, String>> errors) {
        super(message);
        this.code = code;
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<Map<String, String>> getErrors() {
        return errors;
    }
}
