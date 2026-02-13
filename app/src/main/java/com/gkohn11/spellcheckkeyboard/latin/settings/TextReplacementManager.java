/*
 * Copyright (C) 2025 Raimondas Rimkus
 * 
 * This file is part of Simple Spellcheck, a derivative work based on
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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager for text replacement functionality in the keyboard.
 * Loads replacements from CSV and provides lookup functionality.
 */
public class TextReplacementManager {
    private static final String TAG = "TextReplacementManager";
    /** Same prefs name and key as TextReplacementActivity for counter on/off */
    private static final String PREFS_NAME = "TextReplacementActivity";
    private static final String PREFS_COUNTER_VISIBLE = "text_replacement_counter_visible";

    private static TextReplacementManager sInstance;
    private Map<String, String> mReplacements;
    private Map<String, Boolean> mAlwaysOnMap;
    private Context mContext;
    private boolean mInitialized = false;

    private TextReplacementManager(Context context) {
        mContext = context.getApplicationContext();
        mReplacements = new HashMap<>();
        mAlwaysOnMap = new HashMap<>();
    }

    public static synchronized TextReplacementManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TextReplacementManager(context);
        }
        return sInstance;
    }

    /**
     * Initialize and load replacements from CSV
     */
    public void initialize() {
        if (mInitialized) {
            return;
        }
        loadReplacements();
        mInitialized = true;
    }

    /**
     * Reload replacements from CSV (call when replacements are updated)
     */
    public void reload() {
        mReplacements.clear();
        mAlwaysOnMap.clear();
        loadReplacements();
    }

    private void loadReplacements() {
        try {
            // Initialize default CSV if needed
            TextReplacementCsvManager.initializeDefaultCsv(mContext);
            
            // Load from storage
            List<TextReplacementEntry> entries = TextReplacementCsvManager.loadCsvFromStorage(mContext);
            
            // Build lookup maps. ^ on misspell = exact case match (key stored as "^im"); no ^ = case-insensitive (key "im").
            for (TextReplacementEntry entry : entries) {
                String misspell = entry.getMisspell();
                String correct = entry.getCorrect();
                if (misspell != null && !misspell.trim().isEmpty()) {
                    // Trim to avoid whitespace issues
                    misspell = misspell.trim();
                    correct = correct != null ? correct.trim() : "";
                    String key = misspell.startsWith("^") ? misspell : misspell.toLowerCase();
                    mReplacements.put(key, correct);
                    mAlwaysOnMap.put(key, entry.isAlwaysOn());
                    Log.d(TAG, "Loaded replacement: key='" + key + "' -> '" + correct + "'");
                }
            }
            
            Log.d(TAG, "Loaded " + mReplacements.size() + " text replacements");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load text replacements", e);
        }
    }

    /**
     * Resolve the ^ prefix on Correct: "^I'm" means use exact case "I'm" (strip ^, no case matching).
     */
    public static String resolveCapitalizePrefix(String replacement) {
        if (replacement == null || !replacement.startsWith("^")) {
            return replacement;
        }
        return replacement.length() > 1 ? replacement.substring(1) : "";
    }

    /**
     * Get replacement for word. ^ on misspell = exact case only: "^im" matches only "im", not "IM" or "Im".
     * @param word The word as typed (case matters when misspell has ^)
     * @return The raw correct spelling if found, null otherwise
     */
    public String getReplacement(String word) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        // Exact-case match first (misspell stored as "^im")
        String exact = mReplacements.get("^" + word);
        if (exact != null) {
            Log.d(TAG, "getReplacement exact match: word='" + word + "' -> '" + exact + "'");
            return exact;
        }
        String result = mReplacements.get(word.toLowerCase());
        if (result != null) {
            Log.d(TAG, "getReplacement case-insensitive: word='" + word + "' -> '" + result + "'");
        }
        return result;
    }

    /**
     * Check if a replacement should always be applied automatically (same key logic as getReplacement).
     */
    public boolean isAlwaysOn(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        Boolean alwaysOn = mAlwaysOnMap.get("^" + word);
        if (alwaysOn != null) {
            return alwaysOn;
        }
        alwaysOn = mAlwaysOnMap.get(word.toLowerCase());
        return alwaysOn != null && alwaysOn;
    }

    /**
     * Whether usage counting is enabled (when "Turn off counter" is not set).
     */
    public static boolean isCounterEnabled(Context context) {
        if (context == null) {
            return true;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREFS_COUNTER_VISIBLE, true);
    }

    /**
     * Increment the counter for a word when it's corrected (no-op if counter is off).
     * @param word The word that was corrected (case-insensitive)
     */
    public void incrementCounter(String word) {
        if (word == null || word.isEmpty() || mContext == null) {
            return;
        }
        if (!isCounterEnabled(mContext)) {
            return;
        }
        try {
            // Load current entries
            List<TextReplacementEntry> entries = TextReplacementCsvManager.loadCsvFromStorage(mContext);
            String wordLower = word.toLowerCase();
            
            // Find and increment the counter (match exact-case ^misspell or case-insensitive misspell)
            boolean found = false;
            for (TextReplacementEntry entry : entries) {
                String misspell = entry.getMisspell();
                if (misspell == null) continue;
                boolean match = misspell.startsWith("^")
                    ? misspell.equals("^" + word)
                    : misspell.toLowerCase().equals(wordLower);
                if (match) {
                    entry.incrementCounter();
                    found = true;
                    break;
                }
            }
            
            // Save updated entries back to CSV
            if (found) {
                TextReplacementCsvManager.saveCsvToStorage(mContext, entries);
                // Reload to update the manager's internal state
                reload();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to increment counter for word: " + word, e);
        }
    }

    /**
     * Extract the last word from text before cursor
     * @param textBeforeCursor Text before the cursor
     * @return The last word, or null if no word found
     */
    public static String extractLastWord(String textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.isEmpty()) {
            return null;
        }
        
        // Trim any trailing whitespace first
        textBeforeCursor = textBeforeCursor.trim();
        if (textBeforeCursor.isEmpty()) {
            return null;
        }
        
        // Find the start of the last word (skip whitespace and punctuation from the end)
        int end = textBeforeCursor.length();
        while (end > 0 && !Character.isLetterOrDigit(textBeforeCursor.charAt(end - 1))) {
            end--;
        }
        
        if (end == 0) {
            return null;
        }
        
        // Find the start of the word
        int start = end - 1;
        while (start > 0 && Character.isLetterOrDigit(textBeforeCursor.charAt(start - 1))) {
            start--;
        }
        
        return textBeforeCursor.substring(start, end);
    }
    
    /**
     * Extract the last word with any trailing punctuation
     * Supported punctuation: period, comma, exclamation, semicolon, colon, question mark,
     * quotations, parentheses, slashes, brackets
     * @param textBeforeCursor Text before the cursor
     * @return Array with [0] = word, [1] = punctuation (or empty string), or null if no word found
     */
    public static String[] extractLastWordWithPunctuation(String textBeforeCursor) {
        if (textBeforeCursor == null || textBeforeCursor.isEmpty()) {
            return null;
        }
        
        // Trim any trailing whitespace first
        textBeforeCursor = textBeforeCursor.trim();
        if (textBeforeCursor.isEmpty()) {
            return null;
        }
        
        int textEnd = textBeforeCursor.length();
        
        // Check for trailing punctuation
        // Supported: . , ! ; : ? " ' ( ) / \ [ ]
        String punctuation = "";
        if (textEnd > 0) {
            char lastChar = textBeforeCursor.charAt(textEnd - 1);
            if (lastChar == '.' || lastChar == ',' || lastChar == '!' || 
                lastChar == ';' || lastChar == ':' || lastChar == '?' ||
                lastChar == '"' || lastChar == '\'' ||
                lastChar == '(' || lastChar == ')' ||
                lastChar == '/' || lastChar == '\\' ||
                lastChar == '[' || lastChar == ']') {
                punctuation = String.valueOf(lastChar);
                textEnd--; // Exclude punctuation from word extraction
            }
        }
        
        // Find the start of the last word (skip whitespace from the end)
        int end = textEnd;
        while (end > 0 && !Character.isLetterOrDigit(textBeforeCursor.charAt(end - 1))) {
            end--;
        }
        
        if (end == 0) {
            return null;
        }
        
        // Find the start of the word
        int start = end - 1;
        while (start > 0 && Character.isLetterOrDigit(textBeforeCursor.charAt(start - 1))) {
            start--;
        }
        
        String word = textBeforeCursor.substring(start, end);
        return new String[]{word, punctuation};
    }
}

