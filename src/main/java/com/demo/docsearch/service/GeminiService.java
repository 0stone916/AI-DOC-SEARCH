package com.demo.docsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
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
        log.info("[RAG] apiKey 확인: {}", apiKey);
        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                        + apiKey;
        String prompt = "당신은 문서 검색 서비스의 답변 도우미입니다. 제공된 컨텍스트 정보를 바탕으로 질문에 답변해 주세요.\\n\\n" + context
                + "\\n\\n질문: " + question;

        Map<String, Object> body = Map.of("contents",
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        log.info("[RAG] Gemini 답변 생성 요청: {}", question);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candidates = root.path("candidates");

            if (candidates.isMissingNode() || candidates.size() == 0) {
                log.warn("[RAG] Gemini 응답 후보가 없습니다.");
                return "죄송합니다. 답변을 생성할 수 없습니다.";
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            for (JsonNode part : parts) {
                String text = part.path("text").asText("");
                if (!text.isEmpty()) {
                    log.info("[RAG] Gemini 답변 생성 완료");
                    return text;
                }
            }
            return "답변을 찾을 수 없습니다.";

        } catch (HttpStatusCodeException e) {
            log.error("[RAG] Gemini API 호출 실패: {} {} - 상세 내용: {}", e.getStatusCode(),
                    e.getStatusText(), e.getResponseBodyAsString());
            throw new IOException("Gemini API Ask Error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[RAG] 예상치 못한 오류 발생", e);
            throw new IOException("Gemini API Unexpected Error", e);
        }
    }
}


