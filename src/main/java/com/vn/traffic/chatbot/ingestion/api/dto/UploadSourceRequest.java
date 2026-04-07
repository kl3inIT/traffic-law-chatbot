package com.vn.traffic.chatbot.ingestion.api.dto;

import jakarta.validation.constraints.NotBlank;

public class UploadSourceRequest {

    @NotBlank
    private String title;

    private String publisherName;

    private String createdBy;

    public UploadSourceRequest() {}

    public UploadSourceRequest(String title, String publisherName, String createdBy) {
        this.title = title;
        this.publisherName = publisherName;
        this.createdBy = createdBy;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
