package org.institutonata.suprihub.shared.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

    public NotFoundException(String what) {
        super("NOT_FOUND", what + " não encontrado(a).", HttpStatus.NOT_FOUND);
    }
}
