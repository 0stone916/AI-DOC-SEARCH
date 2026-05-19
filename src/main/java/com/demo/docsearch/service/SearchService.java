package com.demo.docsearch.service;

import com.demo.docsearch.model.Document;
import com.demo.docsearch.model.SearchResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private DriveService driveService;

    @Autowired
    private GeminiService geminiService;

    @Value("${app.webhook-url}")
    private String webhookUrl;

    private List<Document> documents;
    private final Map<Long, Long> recentlyProcessed = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            documents = driveService.loadDocuments();
            log.info("[DRIVE] 문서 로드 완료: {}건", documents.size());
        } catch (Exception e) {
            log.error("[DRIVE] 문서 로드 실패: {}", e.getMessage());
            documents = new ArrayList<>();
        }

        for (Document doc : documents) {
            try {
                doc.setEmbedding(
                        embeddingService.getEmbedding(doc.getTitle() + " " + doc.getContent()));
                log.info("[EMBED] 임베딩 생성: {}", doc.getTitle());
            } catch (Exception e) {
                log.warn("[EMBED] 임베딩 생성 실패: {} - {}", doc.getTitle(), e.getMessage());
            }
        }
        log.info("[EMBED] 전체 임베딩 완료: {}건 (Vertex AI text-embedding-004)", documents.size());

        try {
            driveService.initWatch(webhookUrl);
        } catch (Exception e) {
            log.error("[WATCH] Drive 감시 등록 실패: {}", e.getMessage());
        }
    }

    public void handleDriveChange() {
        try {
            List<Document> changedDocs = driveService.getChangedDocuments();
            long now = System.currentTimeMillis();
            for (Document doc : changedDocs) {
                Long lastProcessedAt = recentlyProcessed.get(doc.getId());
                if (lastProcessedAt != null && now - lastProcessedAt < 10_000) continue;
                recentlyProcessed.put(doc.getId(), now);
                try {
                    doc.setEmbedding(
                            embeddingService.getEmbedding(doc.getTitle() + " " + doc.getContent()));
                } catch (Exception e) {
                    log.warn("[WEBHOOK] 임베딩 생성 실패: {} - {}", doc.getTitle(), e.getMessage());
                    continue;
                }
                documents.removeIf(d -> d.getId().equals(doc.getId()));
                documents.add(doc);
                log.info("[WEBHOOK] 변경 감지 → 재임베딩 완료: {}", doc.getTitle());
            }
        } catch (Exception e) {
            log.error("[WEBHOOK] Drive 변경 처리 실패: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 6 * 24 * 60 * 60 * 1000L, initialDelay = 6 * 24 * 60 * 60 * 1000L)
    public void renewWatch() {
        try {
            driveService.initWatch(webhookUrl);
            log.info("[WATCH] Drive 감시 갱신 완료");
        } catch (Exception e) {
            log.error("[WATCH] Drive 감시 갱신 실패: {}", e.getMessage());
        }
    }

    public List<SearchResult> semanticSearch(String query) throws IOException {
        log.info("[SEARCH] 쿼리: \"{}\" → 전체 {}건 유사도 계산", query, documents.size());
        double[] queryEmbedding = embeddingService.getEmbedding(query);
        log.info("[SEARCH] 쿼리 임베딩 앞 3값: [{}, {}, {}]",
                String.format("%.4f", queryEmbedding[0]),
                String.format("%.4f", queryEmbedding[1]),
                String.format("%.4f", queryEmbedding[2]));

        List<SearchResult> all = documents.stream().filter(doc -> doc.getEmbedding() != null)
                .map(doc -> new SearchResult(doc.getId(), doc.getTitle(), doc.getType(),
                        doc.getDate(), cosineSimilarity(queryEmbedding, doc.getEmbedding())))
                .sorted(Comparator.comparingDouble(SearchResult::getSimilarity).reversed())
                .collect(Collectors.toList());

        all.forEach(r -> log.info("[SEARCH] {}: {}%", r.getTitle(),
                String.format("%.1f", r.getSimilarity() * 100)));

        return all.stream().limit(5).collect(Collectors.toList());
    }

    public String askQuestion(String question) throws IOException {
        List<SearchResult> topDocs = semanticSearch(question);
        StringBuilder context = new StringBuilder();
        for (SearchResult result : topDocs) {
            documents.stream()
                    .filter(d -> d.getId().equals(result.getId()))
                    .findFirst()
                    .ifPresent(doc -> {
                        context.append("=== ").append(doc.getTitle()).append(" ===\n");
                        context.append(doc.getContent()).append("\n\n");
                    });
        }
        log.info("[RAG] 상위 {}건 컨텍스트 → Gemini 전달", topDocs.size());
        return geminiService.ask(context.toString(), question);
    }

    public List<SearchResult> likeSearch(String query) {
        String[] keywords = query.toLowerCase().split("\\s+");

        return documents.stream().filter(doc -> {
            String text = (doc.getTitle() + " " + doc.getContent()).toLowerCase();
            return Arrays.stream(keywords).anyMatch(text::contains);
        }).map(doc -> new SearchResult(doc.getId(), doc.getTitle(), doc.getType(), doc.getDate(),
                0.0)).collect(Collectors.toList());
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
