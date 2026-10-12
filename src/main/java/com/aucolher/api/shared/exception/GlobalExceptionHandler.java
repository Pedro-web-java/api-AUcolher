package com.aucolher.api.shared.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusiness(BusinessException ex) {
        return build(HttpStatus.BAD_REQUEST, "Erro de validação", ex.getMessage(), null);
    }

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponseDTO> handleInvalidCredentials(RuntimeException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Não autorizado", ex.getMessage(), null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponseDTO> handleForbidden(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, "Acesso negado", ex.getMessage(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage(), null);
    }

    /**
     * Falhas do Bean Validation nos DTOs: devolve o motivo de cada campo em
     * `errors` e usa a primeira mensagem como texto principal.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fieldError -> errors.putIfAbsent(fieldError.getField(), fieldError.isBindingFailure()
                        // Valor que nem chegou a ser convertido (ex: ?species=BIRD) — a mensagem padrão do Spring é técnica
                        ? "Valor inválido para o campo " + fieldError.getField()
                        : fieldError.getDefaultMessage()));

        String message = errors.values().stream().findFirst().orElse("Verifique os campos enviados");

        return build(HttpStatus.BAD_REQUEST, "Dados inválidos", message, errors);
    }

    /**
     * JSON malformado ou corpo ausente. Quando o problema é um valor com o
     * tipo errado (ex: "species": "BIRD", fora do enum), aponta o campo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnreadableBody(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException format && !format.getPath().isEmpty()) {
            String field = format.getPath().get(format.getPath().size() - 1).getFieldName();
            if (field != null) {
                String message = "Valor inválido para o campo " + field;
                return build(HttpStatus.BAD_REQUEST, "Dados inválidos", message, Map.of(field, message));
            }
        }
        return build(HttpStatus.BAD_REQUEST, "Dados inválidos", "Corpo da requisição ausente ou malformado", null);
    }

    /** Parâmetro de rota ou de filtro com tipo errado (ex: /api/animals/abc) — sem isto, virava 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Dados inválidos", "Valor inválido para o parâmetro " + ex.getName(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método não suportado", ex.getMessage(), null);
    }

    /** Rota inexistente — sem isto, caía no handler genérico abaixo e virava 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleRouteNotFound(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Não encontrado", "Rota não encontrada: /" + ex.getResourcePath(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception ex) {
        // Detalhe do erro fica no log do servidor; o cliente recebe só a mensagem genérica
        log.error("Erro não tratado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado", null);
    }

    private ResponseEntity<ErrorResponseDTO> build(HttpStatus status, String error, String message,
                                                   Map<String, String> errors) {
        ErrorResponseDTO body = new ErrorResponseDTO(LocalDateTime.now(), status.value(), error, message, errors);
        return ResponseEntity.status(status).body(body);
    }
}
