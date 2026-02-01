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

import android.content.Context;
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
            
            // Build lookup maps
            for (TextReplacementEntry entry : entries) {
                String misspell = entry.getMisspell();
                if (misspell != null && !misspell.trim().isEmpty()) {
                    mReplacements.put(misspell.toLowerCase(), entry.getCorrect());
                    mAlwaysOnMap.put(misspell.toLowerCase(), entry.isAlwaysOn());
                }
            }
            
            Log.d(TAG, "Loaded " + mReplacements.size() + " text replacements");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load text replacements", e);
        }
    }

    /**
     * Check if a word should be replaced and return the correct spelling
     * @param word The word to check (case-insensitive)
     * @return The correct spelling if found, null otherwise
     */
    public String getReplacement(String word) {
        if (word == null || word.isEmpty()) {
            return null;
        }
        return mReplacements.get(word.toLowerCase());
    }

    /**
     * Check if a replacement should always be applied automatically
     * @param word The word to check (case-insensitive)
     * @return true if always on, false otherwise
     */
    public boolean isAlwaysOn(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        Boolean alwaysOn = mAlwaysOnMap.get(word.toLowerCase());
        return alwaysOn != null && alwaysOn;
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

