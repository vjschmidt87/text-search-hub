package com.textsearchhub.controller;

import com.textsearchhub.dto.SearchResultDto;
import com.textsearchhub.service.AcademicService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/academic")
@CrossOrigin
public class AcademicController {

    private final AcademicService academicService;

    public AcademicController(AcademicService academicService) {
        this.academicService = academicService;
    }

    @GetMapping("/search")
    public List<SearchResultDto> search(@RequestParam String q) {
        return academicService.search(q);
    }
}
