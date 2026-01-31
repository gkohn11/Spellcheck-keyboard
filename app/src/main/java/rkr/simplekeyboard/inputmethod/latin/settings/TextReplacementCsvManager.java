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

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages loading and saving text replacement data from/to CSV files.
 */
public class TextReplacementCsvManager {
    private static final String TAG = "TextReplacementCsvManager";
    private static final String ASSETS_FILE = "text_replacements.csv";
    private static final String STORAGE_FILE = "text_replacements.csv";

    /**
     * Load default CSV from assets and copy to internal storage if it doesn't exist.
     */
    public static void initializeDefaultCsv(Context context) {
        File storageFile = getStorageFile(context);
        if (storageFile.exists()) {
            return; // Already initialized
        }

        try {
            List<TextReplacementEntry> entries = loadDefaultCsv(context);
            if (!entries.isEmpty()) {
                saveCsvToStorage(context, entries);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize default CSV", e);
        }
    }

    /**
     * Load CSV from assets (default template).
     */
    public static List<TextReplacementEntry> loadDefaultCsv(Context context) {
        List<TextReplacementEntry> entries = new ArrayList<>();
        InputStream inputStream = null;
        BufferedReader reader = null;

        try {
            inputStream = context.getAssets().open(ASSETS_FILE);
            reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    // Skip header line
                    isFirstLine = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                TextReplacementEntry entry = TextReplacementEntry.fromCsv(line);
                if (entry.getMisspell() != null && !entry.getMisspell().isEmpty()) {
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load default CSV from assets", e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }

        return entries;
    }

    /**
     * Load CSV from internal storage.
     */
    public static List<TextReplacementEntry> loadCsvFromStorage(Context context) {
        List<TextReplacementEntry> entries = new ArrayList<>();
        File storageFile = getStorageFile(context);

        if (!storageFile.exists()) {
            // Initialize from default if storage file doesn't exist
            initializeDefaultCsv(context);
            if (!storageFile.exists()) {
                return entries; // Still doesn't exist, return empty list
            }
        }

        FileInputStream fileInputStream = null;
        BufferedReader reader = null;

        try {
            fileInputStream = new FileInputStream(storageFile);
            
            // Check for UTF-8 BOM and skip it if present
            java.io.PushbackInputStream pushbackInputStream = new java.io.PushbackInputStream(fileInputStream, 3);
            byte[] bom = new byte[3];
            int bytesRead = pushbackInputStream.read(bom, 0, 3);
            if (bytesRead == 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF) {
                // BOM found, skip it
            } else if (bytesRead > 0) {
                // No BOM or partial read, push back the bytes
                pushbackInputStream.unread(bom, 0, bytesRead);
            }
            
            reader = new BufferedReader(new InputStreamReader(pushbackInputStream, StandardCharsets.UTF_8));
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    // Skip header line
                    isFirstLine = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                TextReplacementEntry entry = TextReplacementEntry.fromCsv(line);
                if (entry.getMisspell() != null && !entry.getMisspell().isEmpty()) {
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load CSV from storage", e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }

        return entries;
    }

    /**
     * Save entries to CSV file in internal storage.
     */
    public static void saveCsvToStorage(Context context, List<TextReplacementEntry> entries) {
        File storageFile = getStorageFile(context);
        FileOutputStream fileOutputStream = null;
        OutputStreamWriter writer = null;

        try {
            fileOutputStream = new FileOutputStream(storageFile, false); // false = overwrite, not append
            
            // Write UTF-8 BOM to ensure proper encoding recognition
            fileOutputStream.write(0xEF);
            fileOutputStream.write(0xBB);
            fileOutputStream.write(0xBF);
            
            writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);

            // Write header
            writer.write("Misspell,Correct,Always on?\n");

            // Write entries (filter out empty ones)
            int savedCount = 0;
            for (TextReplacementEntry entry : entries) {
                // Only save entries that have at least a misspell value
                if (entry != null && entry.getMisspell() != null && !entry.getMisspell().trim().isEmpty()) {
                    String csvLine = entry.toCsv();
                    writer.write(csvLine + "\n");
                    savedCount++;
                }
            }

            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save CSV to storage: " + storageFile.getAbsolutePath(), e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }

    /**
     * Get the storage file path.
     */
    private static File getStorageFile(Context context) {
        return new File(context.getFilesDir(), STORAGE_FILE);
    }
}


