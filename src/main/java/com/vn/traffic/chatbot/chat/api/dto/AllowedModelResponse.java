package com.vn.traffic.chatbot.chat.api.dto;

/**
 * Response DTO for GET /api/v1/admin/allowed-models.
 *
 * <p>Field names match the frontend {@code AllowedModel} interface in {@code types/api.ts}:
 * <pre>
 * export interface AllowedModel {
 *   modelId: string;
 *   displayName: string;
 * }
 * </pre>
 */
public record AllowedModelResponse(String modelId, String displayName) {}
