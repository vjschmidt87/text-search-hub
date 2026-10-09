package com.textsearchhub.service;

import com.textsearchhub.dto.DownloadLinksDto;
import com.textsearchhub.dto.SearchResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class AcademicService {

    private static final Logger log = LoggerFactory.getLogger(AcademicService.class);
    private final RestTemplate restTemplate;

    public AcademicService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<SearchResultDto> search(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

        CompletableFuture<List<SearchResultDto>> arxivFuture =
            CompletableFuture.supplyAsync(() -> searchArxiv(query, encoded));
        CompletableFuture<List<SearchResultDto>> s2Future =
            CompletableFuture.supplyAsync(() -> searchSemanticScholar(encoded));

        CompletableFuture.allOf(arxivFuture, s2Future).join();

        List<SearchResultDto> results = new ArrayList<>();
        try { results.addAll(arxivFuture.get()); } catch (Exception e) { log.warn("arXiv failed: {}", e.getMessage()); }
        try { results.addAll(s2Future.get()); } catch (Exception e) { log.warn("SemanticScholar failed: {}", e.getMessage()); }

        return results;
    }

    private List<SearchResultDto> searchArxiv(String query, String encoded) {
        try {
            String url = "http://export.arxiv.org/api/query?search_query=all:" + encoded + "&max_results=15";
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null) return List.of();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList entries = doc.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry");
            List<SearchResultDto> dtos = new ArrayList<>();

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String title = getTextContent(entry, "title");
                String summary = getTextContent(entry, "summary");
                String published = getTextContent(entry, "published");
                String year = published != null && published.length() >= 4 ? published.substring(0, 4) : null;

                // Extract author names
                NodeList authorNodes = entry.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "author");
                List<String> authorNames = new ArrayList<>();
                for (int j = 0; j < Math.min(authorNodes.getLength(), 3); j++) {
                    Element authorEl = (Element) authorNodes.item(j);
                    NodeList nameNodes = authorEl.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "name");
                    if (nameNodes.getLength() > 0) authorNames.add(nameNodes.item(0).getTextContent().trim());
                }
                String authors = authorNames.isEmpty() ? "Unknown" : String.join(", ", authorNames);
                if (authorNames.size() >= 3) authors += " et al.";

                // Find PDF link
                String pdfUrl = null;
                NodeList links = entry.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "link");
                for (int j = 0; j < links.getLength(); j++) {
                    Element link = (Element) links.item(j);
                    String rel = link.getAttribute("rel");
                    String type = link.getAttribute("type");
                    if ("related".equals(rel) && "application/pdf".equals(type)) {
                        pdfUrl = link.getAttribute("href");
                        break;
                    }
                }
                // Fallback: build PDF URL from abs URL
                if (pdfUrl == null) {
                    for (int j = 0; j < links.getLength(); j++) {
                        Element link = (Element) links.item(j);
                        String href = link.getAttribute("href");
                        if (href.contains("arxiv.org/abs/")) {
                            pdfUrl = href.replace("/abs/", "/pdf/") + ".pdf";
                            break;
                        }
                    }
                }

                String desc = summary != null ? summary.trim().replaceAll("\\s+", " ") : null;
                if (desc != null && desc.length() > 300) desc = desc.substring(0, 300) + "...";

                dtos.add(new SearchResultDto(
                    title != null ? title.trim() : "Unknown",
                    authors, year, "arxiv", "pdf", desc,
                    new DownloadLinksDto(pdfUrl, null, null)
                ));
            }
            return dtos;
        } catch (Exception e) {
            log.warn("arXiv search error: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<SearchResultDto> searchSemanticScholar(String encoded) {
        try {
            String url = "https://api.semanticscholar.org/graph/v1/paper/search?query=" + encoded
                + "&fields=title,authors,year,openAccessPdf,abstract&limit=10";

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();

            List<Map<String, Object>> papers = (List<Map<String, Object>>) response.get("data");
            if (papers == null) return List.of();

            List<SearchResultDto> dtos = new ArrayList<>();
            for (Map<String, Object> paper : papers) {
                String title = (String) paper.get("title");
                Object yearObj = paper.get("year");
                String year = yearObj != null ? yearObj.toString() : null;
                String abstractText = (String) paper.get("abstract");

                List<Map<String, Object>> authorList = (List<Map<String, Object>>) paper.get("authors");
                String authors = "Unknown";
                if (authorList != null && !authorList.isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (int i = 0; i < Math.min(3, authorList.size()); i++) {
                        Object name = authorList.get(i).get("name");
                        if (name instanceof String s) names.add(s);
                    }
                    authors = String.join(", ", names);
                    if (authorList.size() > 3) authors += " et al.";
                }

                Map<String, Object> oaPdf = (Map<String, Object>) paper.get("openAccessPdf");
                String pdfUrl = oaPdf != null ? (String) oaPdf.get("url") : null;

                String desc = abstractText;
                if (desc != null && desc.length() > 300) desc = desc.substring(0, 300) + "...";

                dtos.add(new SearchResultDto(
                    title, authors, year, "semanticscholar", "pdf", desc,
                    new DownloadLinksDto(pdfUrl, null, null)
                ));
            }
            return dtos;
        } catch (Exception e) {
            log.warn("SemanticScholar search error: {}", e.getMessage());
            return List.of();
        }
    }

    private String getTextContent(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("http://www.w3.org/2005/Atom", localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        // Fallback without namespace
        nodes = parent.getElementsByTagName(localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        return null;
    }
}
