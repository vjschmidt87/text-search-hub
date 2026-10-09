package com.textsearchhub.dto;

public record QuoteResultDto(
    String quote,
    String author,
    String work,
    String source,
    double confidence
) {}
