package com.ledgerx.command.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String,Object>> illegalState(IllegalStateException ex){
    // Could refine based on message; keep 409 for conflicts (already opened / insufficient funds / idem misuse)
    HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("Insufficient") ? HttpStatus.CONFLICT : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(Map.of("status","error","message",ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String,Object>> other(Exception ex){
    return ResponseEntity.status(500).body(Map.of("status","error","message",ex.getMessage()));
  }
}