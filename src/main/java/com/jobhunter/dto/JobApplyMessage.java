package com.jobhunter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobApplyMessage {
    private Long userId;
    private Long jobId;
    private String jobUrl;
    private String userName;
    private String userEmail;
    private String userPhone;
    private String userSkills;
}