package com.demo.docsearch.controller;

import com.demo.docsearch.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriveWebhookController {

    @Autowired
    private SearchService searchService;

    @PostMapping("/api/drive/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Goog-Resource-State", required = false) String resourceState) {
        if ("sync".equals(resourceState)) {
            return ResponseEntity.ok().build();
        }
        searchService.handleDriveChange();
        return ResponseEntity.ok().build();
    }
}
