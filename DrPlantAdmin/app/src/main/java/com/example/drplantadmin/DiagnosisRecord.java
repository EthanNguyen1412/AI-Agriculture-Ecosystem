package com.example.drplantadmin;

public class DiagnosisRecord {

    public long id;
    public String plantType;
    public String status;
    public String diseaseName;
    public long diagnosedAt;
    public String imagePath;
    public String careAdvice;

    public DiagnosisRecord(
            long id,
            String plantType,
            String status,
            String diseaseName,
            long diagnosedAt,
            String imagePath,
            String careAdvice) {

        this.id = id;
        this.plantType = plantType;
        this.status = status;
        this.diseaseName = diseaseName;
        this.diagnosedAt = diagnosedAt;
        this.imagePath = imagePath;
        this.careAdvice = careAdvice;
    }
}
