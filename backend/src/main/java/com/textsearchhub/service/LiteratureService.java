package com.textsearchhub.service;

import com.textsearchhub.dto.DownloadLinksDto;
import com.textsearchhub.dto.SearchResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LiteratureService {

    private static final Logger log = LoggerFactory.getLogger(LiteratureService.class);
    private static final String UA = "Mozilla/5.0 (compatible; TextSearchHub/1.0; +https://github.com/text-search-hub)";

    // Dedicated thread pool — avoids exhausting ForkJoinPool with many blocking I/O calls
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final RestTemplate restTemplate;

    public LiteratureService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<SearchResultDto> search(String query, String type) {
        CompletableFuture<List<SearchResultDto>> gutendexEnFuture =
            CompletableFuture.supplyAsync(() -> searchGutendex(query, type, "en"), pool);
        CompletableFuture<List<SearchResultDto>> gutendexPtFuture =
            CompletableFuture.supplyAsync(() -> searchGutendex(query, type, "pt"), pool);
        CompletableFuture<List<SearchResultDto>> openLibraryFuture =
            CompletableFuture.supplyAsync(() -> searchOpenLibrary(query, type), pool);
        CompletableFuture<List<SearchResultDto>> archiveEnFuture =
            CompletableFuture.supplyAsync(() -> searchInternetArchive(query, type, "English", true), pool);
        CompletableFuture<List<SearchResultDto>> archivePtFuture =
            CompletableFuture.supplyAsync(() -> searchInternetArchive(query, type, "Portuguese", false), pool);
        CompletableFuture<List<SearchResultDto>> standardEbooksFuture =
            CompletableFuture.supplyAsync(() -> searchStandardEbooks(query), pool);

        CompletableFuture.allOf(
            gutendexEnFuture, gutendexPtFuture,
            openLibraryFuture,
            archiveEnFuture, archivePtFuture,
            standardEbooksFuture
        ).join();

        List<SearchResultDto> results = new ArrayList<>();
        mergeGutendex(results, gutendexEnFuture, gutendexPtFuture);
        addSafe(results, standardEbooksFuture, "StandardEbooks");
        addSafe(results, openLibraryFuture, "OpenLibrary");
        addSafe(results, archiveEnFuture, "Archive-EN");
        addSafe(results, archivePtFuture, "Archive-PT");
        return results;
    }

    // ── Gutendex (Project Gutenberg) ─────────────────────────────────────────
    // Query per-language so we know exactly which download URL goes in which slot.

    @SuppressWarnings("unchecked")
    private List<SearchResultDto> searchGutendex(String query, String type, String lang) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://gutendex.com/books/?search=" + encoded + "&languages=" + lang;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.getOrDefault("results", List.of());

            List<SearchResultDto> dtos = new ArrayList<>();
            for (Map<String, Object> book : results) {
                String title = (String) book.get("title");
                List<Map<String, Object>> authors = (List<Map<String, Object>>) book.get("authors");
                String author = authors != null && !authors.isEmpty() ? (String) authors.get(0).get("name") : "Unknown";
                Map<String, String> formats = (Map<String, String>) book.get("formats");
                String fileUrl = getBestFormat(formats);
                // tag lang in description field for merging; real desc set to source name
                dtos.add(new SearchResultDto(
                    title, author, null, "gutenberg", lang, // 'format' field reused for lang tag
                    "Available on Project Gutenberg",
                    new DownloadLinksDto(
                        "en".equals(lang) ? fileUrl : null,
                        "pt".equals(lang) ? fileUrl : null,
                        "pt".equals(lang) ? fileUrl : null
                    )
                ));
            }
            return dtos;
        } catch (Exception e) {
            log.warn("Gutendex {} error: {}", lang, e.getMessage());
            return List.of();
        }
    }

    // Merge EN and PT Gutendex results: same title → combine download links
    private void mergeGutendex(List<SearchResultDto> out,
                                CompletableFuture<List<SearchResultDto>> enFuture,
                                CompletableFuture<List<SearchResultDto>> ptFuture) {
        List<SearchResultDto> enList = safeGet(enFuture, "gutendex-en");
        List<SearchResultDto> ptList = safeGet(ptFuture, "gutendex-pt");

        Map<String, SearchResultDto> byTitle = new LinkedHashMap<>();
        for (SearchResultDto r : enList) {
            byTitle.put(r.title().toLowerCase(), r);
        }
        for (SearchResultDto r : ptList) {
            String key = r.title().toLowerCase();
            if (byTitle.containsKey(key)) {
                // Merge: combine PT link into the EN entry
                SearchResultDto en = byTitle.get(key);
                byTitle.put(key, new SearchResultDto(
                    en.title(), en.author(), en.year(), "gutenberg", "epub",
                    en.description(),
                    new DownloadLinksDto(
                        en.downloads().en(),
                        r.downloads().ptPt(),
                        r.downloads().ptBr()
                    )
                ));
            } else {
                // PT-only title — fix format field
                byTitle.put(key, new SearchResultDto(
                    r.title(), r.author(), r.year(), "gutenberg", "epub",
                    r.description(), r.downloads()
                ));
            }
        }
        // Add EN entries not already covered
        for (SearchResultDto r : enList) {
            String key = r.title().toLowerCase();
            if (byTitle.get(key) != null && "en".equals(byTitle.get(key).format())) {
                // Was never merged with PT — fix format field
                SearchResultDto existing = byTitle.get(key);
                byTitle.put(key, new SearchResultDto(
                    existing.title(), existing.author(), existing.year(), "gutenberg", "epub",
                    existing.description(), existing.downloads()
                ));
            }
        }
        out.addAll(byTitle.values());
    }

    // ── Standard Ebooks ──────────────────────────────────────────────────────
    // Free, DRM-free epub, English only. OPDS Atom XML.

    private List<SearchResultDto> searchStandardEbooks(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://standardebooks.org/feeds/opds/all?query=" + encoded + "&per-page=15";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", UA);
            headers.set("Accept", "application/atom+xml,application/xml,text/xml,*/*");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            String xml = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
            if (xml == null) return List.of();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList entries = doc.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry");
            List<SearchResultDto> dtos = new ArrayList<>();

            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String title = getAtomText(entry, "title");
                String summary = getAtomText(entry, "summary");

                NodeList authorNodes = entry.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "author");
                String author = "Unknown";
                if (authorNodes.getLength() > 0) {
                    NodeList nameNodes = ((Element) authorNodes.item(0))
                        .getElementsByTagNameNS("http://www.w3.org/2005/Atom", "name");
                    if (nameNodes.getLength() > 0) author = nameNodes.item(0).getTextContent().trim();
                }

                // Find the recommended open-access epub link
                String epubUrl = null;
                NodeList links = entry.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "link");
                for (int j = 0; j < links.getLength(); j++) {
                    Element link = (Element) links.item(j);
                    if ("http://opds-spec.org/acquisition/open-access".equals(link.getAttribute("rel"))
                            && "application/epub+zip".equals(link.getAttribute("type"))) {
                        epubUrl = link.getAttribute("href");
                        break;
                    }
                }

                String desc = summary != null ? summary.trim().replaceAll("\\s+", " ") : null;
                if (desc != null && desc.length() > 300) desc = desc.substring(0, 300) + "...";

                dtos.add(new SearchResultDto(
                    title != null ? title.trim() : "Unknown",
                    author, null, "standardebooks", "epub", desc,
                    new DownloadLinksDto(epubUrl, null, null)
                ));
            }
            return dtos;
        } catch (Exception e) {
            log.warn("StandardEbooks search error: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Open Library ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<SearchResultDto> searchOpenLibrary(String query, String type) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String param = "author".equals(type) ? "author" : "title";
            String url = "https://openlibrary.org/search.json?" + param + "=" + encoded
                + "&fields=key,title,author_name,languages,ia,first_publish_year&limit=15";

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();

            List<Map<String, Object>> docs = (List<Map<String, Object>>) response.get("docs");
            if (docs == null) return List.of();

            List<SearchResultDto> dtos = new ArrayList<>();
            for (Map<String, Object> doc : docs) {
                String title = (String) doc.get("title");
                List<String> authorNames = (List<String>) doc.get("author_name");
                String author = authorNames != null && !authorNames.isEmpty() ? authorNames.get(0) : "Unknown";
                Object yearObj = doc.get("first_publish_year");
                String year = yearObj != null ? yearObj.toString() : null;

                List<String> iaList = (List<String>) doc.get("ia");
                if (iaList == null || iaList.isEmpty()) continue;
                String ia = iaList.get(0);
                String pdfUrl = "https://archive.org/download/" + ia + "/" + ia + ".pdf";
                String epubUrl = "https://archive.org/download/" + ia + "/" + ia + ".epub";

                List<String> languages = (List<String>) doc.get("languages");
                boolean isEng = languages == null || languages.isEmpty()
                    || languages.stream().anyMatch(l -> l.startsWith("eng"));
                boolean isPor = languages != null && languages.stream().anyMatch(l -> l.startsWith("por"));

                dtos.add(new SearchResultDto(
                    title, author, year, "openlibrary", "pdf",
                    "Available on Open Library / Internet Archive",
                    new DownloadLinksDto(
                        isEng ? pdfUrl : null,
                        isPor ? epubUrl : null,
                        isPor ? epubUrl : null
                    )
                ));
            }
            return dtos;
        } catch (Exception e) {
            log.warn("OpenLibrary search error: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Internet Archive ─────────────────────────────────────────────────────
    // Separate queries per language for accurate download button mapping.

    @SuppressWarnings("unchecked")
    private List<SearchResultDto> searchInternetArchive(String query, String type, String language, boolean isEnglish) {
        try {
            String baseQ = buildIAQuery(query, type) + " AND language:" + language;
            String encodedQ = URLEncoder.encode(baseQ, StandardCharsets.UTF_8);
            String url = "https://archive.org/advancedsearch.php?q=" + encodedQ
                + "&fl[]=identifier,title,creator,year"
                + "&output=json&rows=12&page=1";

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return List.of();

            Map<String, Object> responseBody = (Map<String, Object>) response.get("response");
            if (responseBody == null) return List.of();

            List<Map<String, Object>> docs = (List<Map<String, Object>>) responseBody.get("docs");
            if (docs == null) return List.of();

            List<SearchResultDto> dtos = new ArrayList<>();
            for (Map<String, Object> doc : docs) {
                String identifier = (String) doc.get("identifier");
                String title = (String) doc.get("title");
                String creator = extractCreator(doc.get("creator"));
                Object yearObj = doc.get("year");
                String year = yearObj != null ? yearObj.toString() : null;

                String dlBase = "https://archive.org/download/" + identifier + "/" + identifier;
                dtos.add(new SearchResultDto(
                    title != null ? title : "Unknown", creator, year, "archive", "pdf",
                    "Available on Internet Archive",
                    isEnglish
                        ? new DownloadLinksDto(dlBase + ".pdf", null, null)
                        : new DownloadLinksDto(null, dlBase + ".pdf", dlBase + ".pdf")
                ));
            }
            return dtos;
        } catch (Exception e) {
            log.warn("InternetArchive {} error: {}", language, e.getMessage());
            return List.of();
        }
    }

    private String buildIAQuery(String query, String type) {
        // Use broad full-text search — strict creator:"..." fails for many IA records
        return "author".equals(type)
            ? query + " AND mediatype:texts"
            : "title:\"" + query + "\" AND mediatype:texts";
    }

    private String extractCreator(Object creatorObj) {
        if (creatorObj instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0));
        if (creatorObj instanceof String s) return s;
        return "Unknown";
    }

    private String getBestFormat(Map<String, String> formats) {
        if (formats == null) return null;
        String epub = formats.get("application/epub+zip");
        if (epub != null) return epub;
        return formats.get("application/pdf");
    }

    private void addSafe(List<SearchResultDto> out,
                         CompletableFuture<List<SearchResultDto>> f, String label) {
        try { List<SearchResultDto> r = f.get(); if (r != null) out.addAll(r); }
        catch (Exception e) { log.warn("{} failed: {}", label, e.getMessage()); }
    }

    private List<SearchResultDto> safeGet(CompletableFuture<List<SearchResultDto>> f, String label) {
        try { List<SearchResultDto> r = f.get(); return r != null ? r : List.of(); }
        catch (Exception e) { log.warn("{} failed: {}", label, e.getMessage()); return List.of(); }
    }

    private String getAtomText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("http://www.w3.org/2005/Atom", localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        nodes = parent.getElementsByTagName(localName);
        if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        return null;
    }
}
