package com.textsearchhub.dto;

public record SearchResultDto(
    String title,
    String author,
    String year,
    String source,
    String format,
    String description,
    DownloadLinksDto downloads
) {}
