package com.jobhunter.exception;

import com.jobhunter.dto.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(new ApiError(404, ex.getMessage()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiError> handleInvalidPassword(InvalidPasswordException ex) {
        return ResponseEntity.status(401)
                .body(new ApiError(401, ex.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleEmailExists(EmailAlreadyRegisteredException ex) {
        return ResponseEntity.status(400)
                .body(new ApiError(400, ex.getMessage()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ApiError> handleAll(Throwable ex) {
        return ResponseEntity.status(500)
                .body(new ApiError(500, "Something went wrong"));
    }
}