package com.example.drplantadmin.Models;

public final class Plant {
    public static final String COFFEE = "coffee";
    public static final String DURIAN = "durian";

    private Plant() { }

    public static String label(String plant) {
        return DURIAN.equals(plant) ? "Sầu riêng" : "Cà phê";
    }

    /** Tiền tố số gốc: CF-001 (cà phê), SR-001 (sầu riêng). */
    public static String prefix(String plant) {
        return DURIAN.equals(plant) ? "SR" : "CF";
    }
}
