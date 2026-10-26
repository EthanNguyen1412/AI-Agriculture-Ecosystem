package com.example.drplantadmin.Models;

public class Diagnosis {
    public String id;
    public String treeId;
    public long time;
    public String diseaseId;     // null = cây khỏe
    public String diseaseName;   // bản sao tên bệnh, dự phòng khi bệnh bị xóa
    public String imagePath;     // chưa có camera nên có thể null
}
