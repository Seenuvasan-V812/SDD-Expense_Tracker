package com.dailyexpense.user.dto;

/**
 * T112 — Data segment returned by each UserDataPort adapter.
 * serviceName identifies the source bounded context; jsonContent is the raw JSON payload.
 */
public record UserExportSegment(String serviceName, String jsonContent) {}
