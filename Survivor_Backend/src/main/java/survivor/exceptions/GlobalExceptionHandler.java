package survivor.exceptions;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author John Draa
 */

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(String message, Map<String,String> errors)
    {
        public ErrorBody(String message) { this(message, null); }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleInvalidBody(MethodArgumentNotValidException ex)
    {
        var errs = new LinkedHashMap<String,String>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errs.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorBody("validation_error", errs));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorBody> handleParamViolations(ConstraintViolationException ex)
    {
        var errs = new LinkedHashMap<String,String>();
        ex.getConstraintViolations().forEach(v ->
        {
            var path = v.getPropertyPath().toString();
            var key = path.substring(path.lastIndexOf('.') + 1);
            errs.put(key, v.getMessage());
        });
        return ResponseEntity.badRequest().body(new ErrorBody("validation_error", errs));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleRse(org.springframework.web.server.ResponseStatusException ex)
    {
        var msg = ex.getReason();
        if (msg == null || msg.isBlank()) msg = ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorBody(msg));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadable(org.springframework.http.converter.HttpMessageNotReadableException ex)
    {
        return ResponseEntity.badRequest().body(new ErrorBody("invalid_request_body"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> handleDbConstraint(org.springframework.dao.DataIntegrityViolationException ex)
    {
        return ResponseEntity.status(409).body(new ErrorBody("constraint_violation"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NotFoundException ex)
    {
        return ResponseEntity.status(404).body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorBody> handleConflict(ConflictException ex)
    {
        return ResponseEntity.status(409).body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorBody> handleBadRequest(BadRequestException ex)
    {
        return ResponseEntity.badRequest().body(new ErrorBody(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleOther(Exception ex)
    {
        return ResponseEntity.status(500).body(new ErrorBody("internal_error"));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorBody> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {
        var errs = new java.util.LinkedHashMap<String,String>();
        errs.put(ex.getParameterName(), "missing required parameter");
        return ResponseEntity.badRequest().body(new ErrorBody("invalid_request_params", errs));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        var errs = new java.util.LinkedHashMap<String,String>();
        errs.put(ex.getName(), "invalid type");
        return ResponseEntity.badRequest().body(new ErrorBody("invalid_request_params", errs));
    }
}
