package com.weather;

public final class TestLog {
    private TestLog() {}
    public static void log(String name, String steps, String expected, String actual, boolean pass) {
        System.out.println("[TEST] " + name + " | Steps: " + steps + " | Expected: " + expected + " | Actual: " + actual + " | RESULT: " + (pass ? "PASS" : "FAIL"));
    }
}


