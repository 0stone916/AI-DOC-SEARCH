package com.demo.docsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key="
                                + apiKey;

                log.info("[EMBED] 요청 텍스트: \"{}\"", text.substring(0, Math.min(30, text.length())));

                Map<String, Object> body = Map.of("model", "models/gemini-embedding-001", "content",
                                Map.of("parts", List.of(Map.of("text", text))));

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                ResponseEntity<String> response = restTemplate.postForEntity(url,
                                new HttpEntity<>(body, headers), String.class);

                JsonNode values = objectMapper.readTree(response.getBody()).path("embedding")
                                .path("values");

                double[] embedding = new double[values.size()];
                for (int i = 0; i < values.size(); i++) {
                        embedding[i] = values.get(i).asDouble();
                }
                log.info("[EMBED] 응답 임베딩 차원: {}, 앞 3값: [{}, {}, {}]", embedding.length,
                                String.format("%.4f", embedding.length > 0 ? embedding[0] : 0),
                                String.format("%.4f", embedding.length > 1 ? embedding[1] : 0),
                                String.format("%.4f", embedding.length > 2 ? embedding[2] : 0));
                return embedding;
        }
}
