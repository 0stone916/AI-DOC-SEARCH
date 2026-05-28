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
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public double[] getEmbedding(String text) throws IOException {
        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key="
                        + apiKey;

        String debugText = text != null ? text.substring(0, Math.min(30, text.length())) : "";
        log.info("[EMBED] 임베딩 요청 시작: {}...", debugText);

        Map<String, Object> body = Map.of("model", "models/gemini-embedding-001", "content",
                Map.of("parts", List.of(Map.of("text", text))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode values = root.path("embedding").path("values");

            if (values.isMissingNode() || values.size() == 0) {
                log.error("[EMBED] API 응답에 임베딩 값이 없습니다: {}", response.getBody());
                throw new IOException("Failed to get embedding values from API response");
            }

            double[] embedding = new double[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = values.get(i).asDouble();
            }

            log.info("[EMBED] 임베딩 생성 완료 (Dimension: {})", embedding.length);
            return embedding;

        } catch (HttpStatusCodeException e) {
            log.error("[EMBED] API 호출 실패: {} {} - 상세 내용: {}", e.getStatusCode(), e.getStatusText(),
                    e.getResponseBodyAsString());
            throw new IOException("Gemini API Embedding Error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[EMBED] 예상치 못한 오류 발생", e);
            throw new IOException("Gemini API Embedding Unexpected Error", e);
        }
    }
}

