package com.demo.docsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String ask(String context, String question) throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        String prompt = "다음은 회사 내부 문서 목록입니다:\n\n" + context
                + "\n위 문서들을 참고해서 다음 질문에 한국어로 간결하게 답해주세요."
                + "\n주의: 인명, 날짜, 금액, 회사명 등은 문서에 적힌 그대로 사용하고 절대 바꾸지 마세요."
                + "\n질문: " + question;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("[RAG] Gemini 답변 생성 중: \"{}\"", question);
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class
        );

        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.getBody());
        com.fasterxml.jackson.databind.JsonNode candidates = root.path("candidates");
        if (candidates.size() == 0) return "답변을 생성할 수 없습니다.";
        for (com.fasterxml.jackson.databind.JsonNode part : candidates.get(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                String text = part.path("text").asText("");
                if (!text.isEmpty()) return text;
            }
        }
        return "답변을 생성할 수 없습니다.";
    }
}
