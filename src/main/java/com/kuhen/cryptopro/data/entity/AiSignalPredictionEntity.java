package com.kuhen.cryptopro.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_signal_prediction")
public class AiSignalPredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "predicted_at", nullable = false)
    private Instant predictedAt;

    @Column(name = "trend_score", nullable = false)
    private Double trendScore;

    @Column(name = "rsi", nullable = false)
    private Double rsi;

    @Column(name = "volume_spike", nullable = false)
    private Double volumeSpike;

    @Column(name = "open_interest_change", nullable = false)
    private Double openInterestChange;

    @Column(name = "funding_rate", nullable = false)
    private Double fundingRate;

    @Column(name = "probability_score", nullable = false)
    private Double probabilityScore;

    @Column(name = "confidence_score", nullable = false)
    private Double confidenceScore;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getInstrumentId() { return instrumentId; }
    public void setInstrumentId(Long instrumentId) { this.instrumentId = instrumentId; }
    public Long getSignalId() { return signalId; }
    public void setSignalId(Long signalId) { this.signalId = signalId; }
    public Instant getPredictedAt() { return predictedAt; }
    public void setPredictedAt(Instant predictedAt) { this.predictedAt = predictedAt; }
    public Double getTrendScore() { return trendScore; }
    public void setTrendScore(Double trendScore) { this.trendScore = trendScore; }
    public Double getRsi() { return rsi; }
    public void setRsi(Double rsi) { this.rsi = rsi; }
    public Double getVolumeSpike() { return volumeSpike; }
    public void setVolumeSpike(Double volumeSpike) { this.volumeSpike = volumeSpike; }
    public Double getOpenInterestChange() { return openInterestChange; }
    public void setOpenInterestChange(Double openInterestChange) { this.openInterestChange = openInterestChange; }
    public Double getFundingRate() { return fundingRate; }
    public void setFundingRate(Double fundingRate) { this.fundingRate = fundingRate; }
    public Double getProbabilityScore() { return probabilityScore; }
    public void setProbabilityScore(Double probabilityScore) { this.probabilityScore = probabilityScore; }
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
}

