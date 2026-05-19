package com.demo.docsearch.controller;

import com.demo.docsearch.model.SearchRequest;
import com.demo.docsearch.model.SearchResult;
import com.demo.docsearch.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class SearchController {

    @Autowired
    private SearchService searchService;

    @PostMapping("/chat/ask")
    public Map<String, String> ask(@RequestBody Map<String, String> body) {
        try {
            return Map.of("answer", searchService.askQuestion(body.get("question")));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(SearchController.class).error("[CHAT] 오류: {}", e.getMessage());
            return Map.of("answer", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody SearchRequest request) throws IOException {
        List<SearchResult> semantic = searchService.semanticSearch(request.getQuery());
        List<SearchResult> like = searchService.likeSearch(request.getQuery());

        return Map.of(
                "query", request.getQuery(),
                "semantic", semantic,
                "like", like
        );
    }
}
