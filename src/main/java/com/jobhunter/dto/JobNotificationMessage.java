package com.jobhunter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobNotificationMessage {
    private Long userId;
    private Long jobId;
    private String title;
    private String company;
    private String location;
    private String platform;
    private String url;
    private Integer salary;
    private String description;
}