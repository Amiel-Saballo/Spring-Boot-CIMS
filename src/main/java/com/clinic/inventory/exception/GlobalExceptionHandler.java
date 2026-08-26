package com.clinic.inventory.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String,Object> notFound(ResourceNotFoundException ex) { return error(404, ex.getMessage()); }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String,Object> business(BusinessRuleException ex) { return error(409, ex.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> validation(MethodArgumentNotValidException ex) {
        Map<String,Object> result = error(400, "Validation failed");
        result.put("fields", ex.getBindingResult().getFieldErrors().stream().collect(java.util.stream.Collectors.toMap(
                e -> e.getField(), e -> Objects.toString(e.getDefaultMessage(), "invalid"), (a,b) -> a, LinkedHashMap::new)));
        return result;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> badRequest(RuntimeException ex) { return error(400, ex.getMessage()); }

    private Map<String,Object> error(int status, String message) {
        Map<String,Object> out = new LinkedHashMap<>(); out.put("timestamp", OffsetDateTime.now()); out.put("status", status); out.put("message", message); return out;
    }
}
