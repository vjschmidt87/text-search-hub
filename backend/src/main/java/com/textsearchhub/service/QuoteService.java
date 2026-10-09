package com.textsearchhub.service;

import com.textsearchhub.dto.QuoteResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private final RestTemplate restTemplate;

    public QuoteService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<QuoteResultDto> findSource(String quote) {
        // Step 1: try Quotable.io
        Optional<QuoteResultDto> quotableResult = searchQuotable(quote);
        if (quotableResult.isPresent() && quotableResult.get().confidence() >= 0.7) {
            return quotableResult;
        }

        // Step 2: fallback to WikiQuote (takes priority over low-confidence Quotable result)
        Optional<QuoteResultDto> wikiResult = searchWikiQuote(quote);
        if (wikiResult.isPresent()) return wikiResult;

        // Last resort: return low-confidence quotable result if present
        if (quotableResult.isPresent() && quotableResult.get().confidence() >= 0.3) {
            return quotableResult;
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<QuoteResultDto> searchQuotable(String quote) {
        try {
            String encoded = URLEncoder.encode(quote, StandardCharsets.UTF_8);
            String url = "https://api.quotable.io/search/quotes?query=" + encoded + "&limit=5";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return Optional.empty();

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) return Optional.empty();

            Map<String, Object> top = results.get(0);
            String content = (String) top.get("content");
            String author = (String) top.get("author");

            double confidence = computeConfidence(quote, content);

            return Optional.of(new QuoteResultDto(content, author, null, "quotable", confidence));
        } catch (Exception e) {
            log.warn("Quotable search error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<QuoteResultDto> searchWikiQuote(String quote) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "TextSearchHub/1.0 (https://github.com/text-search-hub; educational project)");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // Use first meaningful sentence of the quote for better search
            String searchText = extractSearchPhrase(quote);
            String encoded = URLEncoder.encode(searchText, StandardCharsets.UTF_8);

            // Step 1: search for pages
            String searchUrl = "https://en.wikiquote.org/w/api.php?action=query&list=search"
                + "&srsearch=" + encoded + "&format=json&origin=*&srlimit=3";
            ResponseEntity<Map> searchResp = restTemplate.exchange(searchUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> searchResponse = searchResp.getBody();
            if (searchResponse == null) return Optional.empty();

            Map<String, Object> query = (Map<String, Object>) searchResponse.get("query");
            if (query == null) return Optional.empty();

            List<Map<String, Object>> searchResults = (List<Map<String, Object>>) query.get("search");
            if (searchResults == null || searchResults.isEmpty()) return Optional.empty();

            String pageTitle = (String) searchResults.get(0).get("title");

            // Step 2: get extract for the page
            String encodedTitle = URLEncoder.encode(pageTitle, StandardCharsets.UTF_8);
            String extractUrl = "https://en.wikiquote.org/w/api.php?action=query&prop=extracts"
                + "&titles=" + encodedTitle + "&format=json&origin=*&exchars=2000";
            ResponseEntity<Map> extractResp = restTemplate.exchange(extractUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> extractResponse = extractResp.getBody();
            if (extractResponse == null) return Optional.empty();

            Map<String, Object> pages = (Map<String, Object>) ((Map<String, Object>) extractResponse.get("query")).get("pages");
            if (pages == null || pages.isEmpty()) return Optional.empty();

            Map<String, Object> page = (Map<String, Object>) pages.values().iterator().next();
            String extract = (String) page.get("extract");
            if (extract == null) return Optional.empty();

            // Try to find work title from extract (e.g., "From The Two Towers" or "<i>The Two Towers</i>")
            String work = extractWorkTitle(extract);

            return Optional.of(new QuoteResultDto(quote, pageTitle, work, "wikiquote", 0.75));
        } catch (Exception e) {
            log.warn("WikiQuote search error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private double computeConfidence(String input, String candidate) {
        if (input == null || candidate == null) return 0.0;
        String normalInput = normalize(input);
        String normalCandidate = normalize(candidate);
        if (normalCandidate.contains(normalInput) || normalInput.contains(normalCandidate)) return 0.95;

        // Word overlap ratio
        Set<String> inputWords = new HashSet<>(Arrays.asList(normalInput.split("\\s+")));
        Set<String> candidateWords = new HashSet<>(Arrays.asList(normalCandidate.split("\\s+")));
        long overlap = inputWords.stream().filter(candidateWords::contains).count();
        double ratio = (double) overlap / Math.max(inputWords.size(), 1);
        return Math.min(ratio, 0.9);
    }

    private String normalize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();
    }

    private String extractSearchPhrase(String quote) {
        // Take up to the first comma or 60 chars, whichever comes first
        String trimmed = quote.trim();
        int comma = trimmed.indexOf(',');
        if (comma > 10 && comma < 60) return trimmed.substring(0, comma);
        return trimmed.length() > 60 ? trimmed.substring(0, 60) : trimmed;
    }

    private String extractWorkTitle(String extract) {
        // Look for patterns like: From "The Two Towers", from <i>Title</i>, (Title), etc.
        Pattern[] patterns = {
            Pattern.compile("(?i)from[\\s\"']*<[^>]*>([^<]{3,60})</"),
            Pattern.compile("(?i)from[\\s\"'\"\"]+([A-Z][^,.<\"]{3,60})"),
            Pattern.compile("<i>([^<]{3,60})</i>"),
            Pattern.compile("\\(([A-Z][^)]{3,60})\\)")
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(extract);
            if (m.find()) return m.group(1).trim();
        }
        return null;
    }
}
