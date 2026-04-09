package com.jobhunter.dto;

import lombok.Data;

@Data
public class JobDto {
    private String externalJobId;
    private String title;
    private String company;
    private String location;
    private String platform;
    private String url;
    private Integer salary;
    private String description;
    private String employmentType;
    private String requiredExperience;
}