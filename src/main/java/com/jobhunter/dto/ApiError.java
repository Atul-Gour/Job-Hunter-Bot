package com.jobhunter.dto;

public class ApiError {
    private int status;
    private String message;
    private long timestamp;

    public ApiError(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
}
