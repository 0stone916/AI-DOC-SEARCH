package com.demo.docsearch.model;

public class Document {
    private Long id;
    private String title;
    private String content;
    private String type;
    private String date;
    private double[] embedding;

    public Document() {}

    public Document(Long id, String title, String content, String type, String date) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.type = type;
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getType() {
        return type;
    }

    public String getDate() {
        return date;
    }

    public double[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(double[] embedding) {
        this.embedding = embedding;
    }
}
