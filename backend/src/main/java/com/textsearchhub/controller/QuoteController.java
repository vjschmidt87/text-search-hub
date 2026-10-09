package com.textsearchhub.controller;

import com.textsearchhub.dto.QuoteResultDto;
import com.textsearchhub.dto.QuoteSearchRequest;
import com.textsearchhub.service.QuoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotes")
@CrossOrigin
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping("/search")
    public ResponseEntity<QuoteResultDto> search(@RequestBody QuoteSearchRequest request) {
        return quoteService.findSource(request.quote())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
