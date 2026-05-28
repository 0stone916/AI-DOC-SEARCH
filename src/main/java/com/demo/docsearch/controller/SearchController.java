package com.demo.docsearch.controller;

import com.demo.docsearch.model.SearchRequest;
import com.demo.docsearch.model.SearchResult;
import com.demo.docsearch.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    @Autowired
    private SearchService searchService;

    @PostMapping("/chat/ask")
    public ResponseEntity<Map<String, String>> ask(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        try {
            String answer = searchService.askQuestion(question);
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            log.error("[CHAT] 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("answer", "죄송합니다. 처리 중 오류가 발생했습니다.", "error", "true"));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody SearchRequest request) {
        try {
            List<SearchResult> semantic = searchService.semanticSearch(request.getQuery());
            List<SearchResult> like = searchService.likeSearch(request.getQuery());
            return ResponseEntity
                    .ok(Map.of("query", request.getQuery(), "semantic", semantic, "like", like));
        } catch (Exception e) {
            log.error("[SEARCH] 검색 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "검색 처리 중 오류가 발생했습니다."));
        }
    }
}


