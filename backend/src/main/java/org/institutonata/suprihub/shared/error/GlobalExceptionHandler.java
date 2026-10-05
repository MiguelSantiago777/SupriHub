package org.institutonata.suprihub.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte exceções em respostas RFC 7807 ({@link ProblemDetail}) com textos em pt-BR.
 * Nunca inclui dados pessoais nem stack traces na resposta.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://suprihub.institutonata.org.br/problemas/";

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setType(URI.create(TYPE_BASE + ex.getCode().toLowerCase().replace('_', '-')));
        pd.setTitle(titleFor(ex.getStatus()));
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return handleBusiness(new BusinessException("INVALID_CREDENTIALS",
                "E-mail ou senha incorretos.", HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return handleBusiness(new BusinessException("ACCESS_DENIED",
                "Você não tem permissão para realizar esta ação.", HttpStatus.FORBIDDEN));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return handleBusiness(new BusinessException("CONCURRENT_UPDATE",
                "Este registro foi alterado por outra pessoa. Recarregue a página e tente novamente.",
                HttpStatus.CONFLICT));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        return validationProblem(errors);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(handleBusiness(new BusinessException(
                "FILE_TOO_LARGE", "O arquivo é maior que o tamanho permitido.", HttpStatus.PAYLOAD_TOO_LARGE)));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado: {}", ex.getClass().getName(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente em instantes.");
        pd.setTitle(titleFor(HttpStatus.INTERNAL_SERVER_ERROR));
        pd.setProperty("code", "INTERNAL_ERROR");
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "Valor inválido." : fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    private ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Alguns campos precisam ser corrigidos.");
        pd.setType(URI.create(TYPE_BASE + "validacao"));
        pd.setTitle("Dados inválidos");
        pd.setProperty("code", "VALIDATION_ERROR");
        pd.setProperty("errors", errors);
        return pd;
    }

    private static String titleFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida";
            case 401 -> "Não autenticado";
            case 403 -> "Acesso negado";
            case 404 -> "Não encontrado";
            case 409 -> "Conflito";
            case 413 -> "Arquivo muito grande";
            case 422 -> "Regra de negócio não atendida";
            case 429 -> "Muitas tentativas";
            default -> "Erro";
        };
    }
}
