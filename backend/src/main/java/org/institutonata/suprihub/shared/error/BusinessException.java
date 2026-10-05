package org.institutonata.suprihub.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Violação de regra de negócio. A mensagem é exibida ao usuário (pt-BR) e o
 * código é estável para o frontend tratar casos específicos.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
