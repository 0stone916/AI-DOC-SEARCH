package com.demo.docsearch.model;

public class SearchResult {
    private Long id;
    private String title;
    private String type;
    private String date;
    private double similarity;

    public SearchResult(Long id, String title, String type, String date, double similarity) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.date = date;
        this.similarity = similarity;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getDate() { return date; }
    public double getSimilarity() { return similarity; }
}
