package com.demo.docsearch.service;

import com.demo.docsearch.model.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.io.IOException;
import java.util.*;;

@Service
public class DriveService {

    private static final Logger log = LoggerFactory.getLogger(DriveService.class);

    @Value("${gcp.drive.folder-id}")
    private String folderId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String changesPageToken;
    private volatile String watchChannelId;
    private volatile String watchResourceId;

    private String getToken() throws IOException {
        GoogleCredentials credentials = GoogleCredentials
                .getApplicationDefault()
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.readonly"));
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    public void initWatch(String webhookUrl) throws IOException {
        String token = getToken();

        URI tokenUri = UriComponentsBuilder
                .fromHttpUrl("https://www.googleapis.com/drive/v3/changes/startPageToken")
                .build().encode().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> tokenResponse = restTemplate.exchange(
                tokenUri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        changesPageToken = objectMapper.readTree(tokenResponse.getBody()).path("startPageToken").asText();

        watchChannelId = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("id", watchChannelId);
        body.put("type", "web_hook");
        body.put("address", webhookUrl);
        body.put("expiration", System.currentTimeMillis() + 6 * 24 * 60 * 60 * 1000L);

        URI watchUri = UriComponentsBuilder
                .fromHttpUrl("https://www.googleapis.com/drive/v3/changes/watch")
                .queryParam("pageToken", changesPageToken)
                .build().encode().toUri();

        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setBearerAuth(token);
        postHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> watchResponse = restTemplate.exchange(
                watchUri, HttpMethod.POST, new HttpEntity<>(body, postHeaders), String.class);
        watchResourceId = objectMapper.readTree(watchResponse.getBody()).path("resourceId").asText();
        log.info("[WATCH] Drive 감시 등록 완료: {}", watchChannelId);
    }

    public List<Document> getChangedDocuments() throws IOException {
        if (changesPageToken == null) return Collections.emptyList();
        String token = getToken();

        URI changesUri = UriComponentsBuilder
                .fromHttpUrl("https://www.googleapis.com/drive/v3/changes")
                .queryParam("pageToken", changesPageToken)
                .queryParam("fields", "nextPageToken,newStartPageToken,changes(removed,file(id,name,mimeType,createdTime,parents,trashed))")
                .build().encode().toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                changesUri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JsonNode root = objectMapper.readTree(response.getBody());

        if (root.has("newStartPageToken")) {
            changesPageToken = root.path("newStartPageToken").asText();
        } else if (root.has("nextPageToken")) {
            changesPageToken = root.path("nextPageToken").asText();
        }

        List<Document> changedDocs = new ArrayList<>();
        for (JsonNode change : root.path("changes")) {
            if (change.path("removed").asBoolean(false)) continue;
            JsonNode file = change.path("file");
            if (file.path("trashed").asBoolean(false)) continue;

            boolean inOurFolder = false;
            for (JsonNode parent : file.path("parents")) {
                if (folderId.equals(parent.asText())) { inOurFolder = true; break; }
            }
            if (!inOurFolder) continue;

            String fileId = file.path("id").asText();
            String fileName = file.path("name").asText();
            String mimeType = file.path("mimeType").asText();
            String createdTime = file.path("createdTime").asText();
            if (createdTime.length() >= 10) createdTime = createdTime.substring(0, 10);

            String content = extractContent(fileId, mimeType, token);
            if (content == null) continue;
            if (content.length() > 1000) content = content.substring(0, 1000);

            changedDocs.add(new Document(
                    (long) Math.abs(fileId.hashCode()),
                    fileName, content.trim(), "", createdTime));
        }
        return changedDocs;
    }

    public List<Document> loadDocuments() throws IOException {
        String token = getToken();

        URI listUri = UriComponentsBuilder
                .fromHttpUrl("https://www.googleapis.com/drive/v3/files")
                .queryParam("q", "'" + folderId + "' in parents and trashed=false")
                .queryParam("fields", "files(id,name,createdTime,mimeType)")
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> listResponse = restTemplate.exchange(
                listUri, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        JsonNode files = objectMapper.readTree(listResponse.getBody()).path("files");

        List<Document> documents = new ArrayList<>();
        for (JsonNode file : files) {
            String fileId = file.path("id").asText();
            String fileName = file.path("name").asText();
            String mimeType = file.path("mimeType").asText();
            String createdTime = file.path("createdTime").asText().substring(0, 10);

            String content = extractContent(fileId, mimeType, token);
            if (content == null) continue;
            if (content.length() > 1000) content = content.substring(0, 1000);

            documents.add(new Document(
                    (long) Math.abs(fileId.hashCode()),
                    fileName,
                    content.trim(),
                    "",
                    createdTime
            ));
        }
        return documents;
    }

    private String extractContent(String fileId, String mimeType, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        try {
            if ("application/vnd.google-apps.document".equals(mimeType)) {
                String exportUrl = "https://www.googleapis.com/drive/v3/files/" + fileId + "/export?mimeType=text/plain";
                ResponseEntity<String> response = restTemplate.exchange(
                        exportUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                return response.getBody();
            } else if ("application/pdf".equals(mimeType)) {
                String downloadUrl = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media";
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        downloadUrl, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
                try (PDDocument pdf = Loader.loadPDF(response.getBody())) {
                    return new PDFTextStripper().getText(pdf);
                }
            }
        } catch (Exception e) {
            log.warn("[DRIVE] 파일 읽기 실패: {} - {}", fileName(fileId), e.getMessage());
        }
        return null;
    }

    private String fileName(String fileId) {
        return fileId;
    }
}
