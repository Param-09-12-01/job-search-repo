package com.jobcopilot.ai;

public record EmailClassification(
    boolean isJobApplication,
    String company,
    String jobTitle,
    double confidence
) {}
