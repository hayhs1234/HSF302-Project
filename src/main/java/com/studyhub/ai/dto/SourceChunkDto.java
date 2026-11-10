package com.studyhub.ai.dto;

public class SourceChunkDto {
    private int citationNumber;
    private Long documentId;
    private String documentTitle;
    private String fileExtension;
    private String subjectName;
    private int chunkIndex;
    private String snippet;
    private double relevanceScore;

    public SourceChunkDto() {
    }

    public SourceChunkDto(Long documentId, String documentTitle, int chunkIndex, String snippet, double relevanceScore) {
        this(1, documentId, documentTitle, "PDF", "", chunkIndex, snippet, relevanceScore);
    }

    public SourceChunkDto(int citationNumber, Long documentId, String documentTitle, String fileExtension,
                          String subjectName, int chunkIndex, String snippet, double relevanceScore) {
        this.citationNumber = citationNumber;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.fileExtension = fileExtension;
        this.subjectName = subjectName;
        this.chunkIndex = chunkIndex;
        this.snippet = snippet;
        this.relevanceScore = relevanceScore;
    }

    public int getCitationNumber() {
        return citationNumber;
    }

    public void setCitationNumber(int citationNumber) {
        this.citationNumber = citationNumber;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
