package com.nckh.drplant.Models;

import java.io.Serializable;

public class DiagnosisHistoryItem implements Serializable {
    public String id;
    public long viewedAt;
    public String imagePath;
    public String message;
    public String diseaseName;
    public String predictionName;
    public String predictionClassId;
    public String plantTypeRaw;
    public String plantTypeNormalized;
    public double confidence;
    public boolean healthy;
    public String severityLevel;
    public String severityLabel;
    public String severityColor;
    public String severityIcon;
    public String symptoms;
    public String causes;
    public String chemicalTitle;
    public String chemicalDescription;
    public String chemicalIcon;
    public String biologicalTitle;
    public String biologicalDescription;
    public String biologicalIcon;
    public DiagnosisResponse diagnosisResponse;
}

