package com.textsearchhub.controller;

import com.textsearchhub.dto.SearchResultDto;
import com.textsearchhub.service.LiteratureService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/literature")
@CrossOrigin
public class LiteratureController {

    private final LiteratureService literatureService;

    public LiteratureController(LiteratureService literatureService) {
        this.literatureService = literatureService;
    }

    @GetMapping("/search")
    public List<SearchResultDto> search(
        @RequestParam String q,
        @RequestParam(defaultValue = "book") String type
    ) {
        return literatureService.search(q, type);
    }
}
