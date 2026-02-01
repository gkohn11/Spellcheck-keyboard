/*
 * Copyright (C) 2025 Raimondas Rimkus
 * 
 * This file is part of Spellcheck Keyboard, a derivative work based on
 * Simple Keyboard (Copyright (C) 2025 Raimondas Rimkus and contributors)
 * which is based on AOSP LatinIME (Copyright (C) 2008 The Android Open Source Project).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gkohn11.spellcheckkeyboard.latin.settings;

/**
 * Data model for a text replacement entry.
 */
public class TextReplacementEntry {
    private String misspell;
    private String correct;
    private boolean alwaysOn;

    public TextReplacementEntry() {
        this("", "", false);
    }

    public TextReplacementEntry(String misspell, String correct, boolean alwaysOn) {
        this.misspell = misspell != null ? misspell : "";
        this.correct = correct != null ? correct : "";
        this.alwaysOn = alwaysOn;
    }

    public String getMisspell() {
        return misspell;
    }

    public void setMisspell(String misspell) {
        this.misspell = misspell != null ? misspell : "";
    }

    public String getCorrect() {
        return correct;
    }

    public void setCorrect(String correct) {
        this.correct = correct != null ? correct : "";
    }

    public boolean isAlwaysOn() {
        return alwaysOn;
    }

    public void setAlwaysOn(boolean alwaysOn) {
        this.alwaysOn = alwaysOn;
    }

    /**
     * Convert to CSV format: misspell,correct,alwaysOn
     */
    public String toCsv() {
        return escapeCsvField(misspell) + "," + 
               escapeCsvField(correct) + "," + 
               alwaysOn;
    }

    /**
     * Parse from CSV line
     */
    public static TextReplacementEntry fromCsv(String csvLine) {
        if (csvLine == null || csvLine.trim().isEmpty()) {
            return new TextReplacementEntry();
        }

        String[] parts = parseCsvLine(csvLine);
        if (parts.length < 3) {
            return new TextReplacementEntry();
        }

        String misspell = unescapeCsvField(parts[0].trim());
        String correct = unescapeCsvField(parts[1].trim());
        boolean alwaysOn = "true".equalsIgnoreCase(parts[2].trim());

        return new TextReplacementEntry(misspell, correct, alwaysOn);
    }

    /**
     * Escape CSV field if it contains comma, quote, or newline
     */
    private static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * Unescape CSV field
     */
    private static String unescapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.startsWith("\"") && field.endsWith("\"")) {
            field = field.substring(1, field.length() - 1);
            field = field.replace("\"\"", "\"");
        }
        return field;
    }

    /**
     * Parse CSV line handling quoted fields
     */
    private static String[] parseCsvLine(String line) {
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote
                    currentField.append('"');
                    i++;
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // Field separator
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString());

        return fields.toArray(new String[fields.size()]);
    }
}


