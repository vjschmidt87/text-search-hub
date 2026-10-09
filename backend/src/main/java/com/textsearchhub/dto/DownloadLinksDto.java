package com.textsearchhub.dto;

public record DownloadLinksDto(
    String en,
    String ptPt,
    String ptBr
) {
    public static DownloadLinksDto empty() {
        return new DownloadLinksDto(null, null, null);
    }
}
