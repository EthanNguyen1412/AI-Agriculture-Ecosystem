package com.nckh.drplant.Models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class DiagnosisResponse implements Serializable {
    @SerializedName("success")
    public boolean success;
    
    @SerializedName("message")
    public String message;
    
    @SerializedName("prediction")
    public Prediction prediction;
    
    @SerializedName("disease")
    public Disease disease;
    
    @SerializedName("solutions")
    public Solutions solutions;

    public static class Prediction implements Serializable {
        @SerializedName("class_id")
        public String classId;
        
        @SerializedName("class_index")
        public int classIndex;
        
        @SerializedName("class_name")
        public String className;
        
        @SerializedName("confidence")
        public double confidence;
        
        @SerializedName("plant_type")
        public String plantType;
        
        @SerializedName("is_healthy")
        public boolean isHealthy;
    }

    public static class Disease implements Serializable {
        @SerializedName("name")
        public String name;
        
        @SerializedName("severity")
        public Severity severity;
        
        @SerializedName("symptoms")
        public String symptoms;
        
        @SerializedName("causes")
        public String causes;
    }

    public static class Severity implements Serializable {
        @SerializedName("level")
        public String level;
        
        @SerializedName("label")
        public String label;
        
        @SerializedName("color")
        public String color;
        
        @SerializedName("icon")
        public String icon;
    }

    public static class Solutions implements Serializable {
        @SerializedName("chemical")
        public Solution chemical;
        
        @SerializedName("biological")
        public Solution biological;
    }

    public static class Solution implements Serializable {
        @SerializedName("title")
        public String title;
        
        @SerializedName("description")
        public String description;
        
        @SerializedName("icon")
        public String icon;
    }
}

