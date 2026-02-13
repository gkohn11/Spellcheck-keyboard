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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.gkohn11.spellcheckkeyboard.R;

/**
 * Activity for managing text replacement entries.
 */
public class TextReplacementActivity extends AppCompatActivity {
    private static final String TAG = "TextReplacementActivity";
    private static final int REQUEST_CODE_PICK_CSV = 1001;
    private static final String PREFS_COUNTER_VISIBLE = "text_replacement_counter_visible";
    private RecyclerView recyclerView;
    private TextReplacementAdapter adapter;
    private List<TextReplacementEntry> entries;
    private AlertDialog searchDialog;
    private String currentSearchTerm = "";
    private int currentMatchIndex = -1;
    private java.util.List<Integer> matchPositions = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_text_replacement);

            // Setup action bar
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setTitle(R.string.settings_screen_text_replacement);
                
                // Add custom view with upload button
                actionBar.setDisplayShowCustomEnabled(true);
                android.view.LayoutInflater inflater = getLayoutInflater();
                View customView = inflater.inflate(R.layout.actionbar_text_replacement, null);
                ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(
                        ActionBar.LayoutParams.MATCH_PARENT,
                        ActionBar.LayoutParams.MATCH_PARENT);
                actionBar.setCustomView(customView, layoutParams);
                
                // Setup upload button in action bar
                Button uploadButton = customView.findViewById(R.id.button_upload);
                if (uploadButton != null) {
                    uploadButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showUploadConfirmationDialog();
                        }
                    });
                }
            }

            // Initialize RecyclerView
            recyclerView = findViewById(R.id.recycler_view);
            if (recyclerView == null) {
                Log.e(TAG, "RecyclerView not found in layout");
                finish();
                return;
            }
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            // Setup export button
            Button exportButton = findViewById(R.id.button_export);
            if (exportButton != null) {
                exportButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        exportToCsv();
                    }
                });
                
                // Setup long press to share CSV
                exportButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        shareCsv();
                        return true;
                    }
                });
            }

            // Setup bottom Search button
            Button searchButton = findViewById(R.id.button_search);
            if (searchButton != null) {
                searchButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showSearchDialog();
                    }
                });
            }

            // Load data
            loadData();

            // Setup header: 3-dots menu (DEL is inside the menu)
            View headerView = findViewById(R.id.header_view);
            if (headerView != null) {
                android.widget.ImageButton menuButton = headerView.findViewById(R.id.button_menu);
                if (menuButton != null) {
                    menuButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showHeaderMenu(v);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            finish();
        }
    }

    private static final int MENU_DEL = 0;
    private static final int MENU_CLEAR_COUNTER = 1;
    private static final int MENU_COUNTER_OFF = 2;
    private static final int MENU_DELETE_UNUSED = 3;
    private static final int MENU_DELETE_ALL = 4;

    /**
     * Show popup menu when 3-dots is pressed: Clear counter, Turn off counter, Delete unused, Delete selected, Delete all.
     */
    private void showHeaderMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_CLEAR_COUNTER, 0, getString(R.string.tr_menu_clear_counter));
        boolean counterVisible = getCounterVisiblePreference();
        popup.getMenu().add(0, MENU_COUNTER_OFF, 0, counterVisible ? getString(R.string.tr_menu_turn_off_counter) : getString(R.string.tr_menu_turn_on_counter));
        popup.getMenu().add(0, MENU_DELETE_UNUSED, 0, getString(R.string.tr_menu_delete_unused));
        popup.getMenu().add(0, MENU_DEL, 0, getString(R.string.tr_menu_delete_selected));
        popup.getMenu().add(0, MENU_DELETE_ALL, 0, getString(R.string.tr_menu_delete_all));
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == MENU_DEL) {
                    if (adapter != null && adapter.hasSelectedEntries()) {
                        adapter.deleteSelectedEntries();
                        saveData();
                        adapter.notifyDataSetChanged();
                    }
                    return true;
                }
                if (item.getItemId() == MENU_CLEAR_COUNTER) {
                    showClearCounterConfirmationDialog();
                    return true;
                }
                if (item.getItemId() == MENU_COUNTER_OFF) {
                    toggleCounterVisible();
                    return true;
                }
                if (item.getItemId() == MENU_DELETE_UNUSED) {
                    showDeleteUnusedConfirmationDialog();
                    return true;
                }
                if (item.getItemId() == MENU_DELETE_ALL) {
                    showDeleteAllConfirmationDialog();
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    private boolean getCounterVisiblePreference() {
        SharedPreferences prefs = getSharedPreferences("TextReplacementActivity", MODE_PRIVATE);
        return prefs.getBoolean(PREFS_COUNTER_VISIBLE, true);
    }

    private void setCounterVisiblePreference(boolean visible) {
        getSharedPreferences("TextReplacementActivity", MODE_PRIVATE)
                .edit()
                .putBoolean(PREFS_COUNTER_VISIBLE, visible)
                .apply();
    }

    private void toggleCounterVisible() {
        boolean newValue = !getCounterVisiblePreference();
        setCounterVisiblePreference(newValue);
        if (adapter != null) {
            adapter.setCounterVisible(newValue);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save data when leaving the activity
        saveData();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Also save on stop to ensure data is persisted
        saveData();
    }

    /**
     * Load data from CSV file and sort by counter (most used first).
     */
    private void loadData() {
        try {
            // Initialize default CSV if needed
            TextReplacementCsvManager.initializeDefaultCsv(this);
            
            // Load from storage
            entries = TextReplacementCsvManager.loadCsvFromStorage(this);
            
            // Sort by counter descending (most used first)
            Collections.sort(entries, new Comparator<TextReplacementEntry>() {
                @Override
                public int compare(TextReplacementEntry a, TextReplacementEntry b) {
                    int ca = a != null ? a.getCounter() : 0;
                    int cb = b != null ? b.getCounter() : 0;
                    return Integer.compare(cb, ca); // descending
                }
            });
            
            // Setup adapter
            adapter = new TextReplacementAdapter(entries);
            adapter.setCounterVisible(getCounterVisiblePreference());
            recyclerView.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load data", e);
            // Create empty list on error
            entries = new java.util.ArrayList<>();
            adapter = new TextReplacementAdapter(entries);
            adapter.setCounterVisible(getCounterVisiblePreference());
            recyclerView.setAdapter(adapter);
        }
    }

    /**
     * Save data to CSV file
     */
    private void saveData() {
        if (adapter != null && adapter.hasDataChanged()) {
            try {
                List<TextReplacementEntry> currentEntries = adapter.getEntries();
                TextReplacementCsvManager.saveCsvToStorage(this, currentEntries);
                adapter.setDataChanged(false);
                // Reload the text replacement manager so changes take effect immediately
                TextReplacementManager.getInstance(this).reload();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save data", e);
            }
        }
    }

    /**
     * Export text replacement data to CSV file and save to Downloads folder
     */
    private void exportToCsv() {
        try {
            // Get current entries from adapter
            List<TextReplacementEntry> entriesToExport;
            if (adapter != null) {
                entriesToExport = adapter.getEntries();
            } else {
                entriesToExport = TextReplacementCsvManager.loadCsvFromStorage(this);
            }

            // Create filename with timestamp
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
            String timestamp = sdf.format(new java.util.Date());
            String fileName = "text_replacements_" + timestamp + ".csv";

            // Save to Downloads folder
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+): Use MediaStore
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);

                android.content.ContentResolver resolver = getContentResolver();
                Uri uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                
                if (uri != null) {
                    try (java.io.OutputStream outputStream = resolver.openOutputStream(uri)) {
                        // Write UTF-8 BOM first and flush to ensure it's written before other content
                        outputStream.write(0xEF);
                        outputStream.write(0xBB);
                        outputStream.write(0xBF);
                        outputStream.flush();
                        
                        // Now create writer and write content
                        try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                            // Write header
                            writer.write("Misspell,Correct,Always on?,Counter\n");

                            // Write entries
                            for (TextReplacementEntry entry : entriesToExport) {
                                if (entry != null && entry.getMisspell() != null && !entry.getMisspell().trim().isEmpty()) {
                                    writer.write(entry.toCsv() + "\n");
                                }
                            }
                            writer.flush();
                        }
                    }
                    
                    Toast.makeText(this, getString(R.string.tr_saved_to_downloads, fileName), Toast.LENGTH_LONG).show();
                } else {
                    throw new Exception("Failed to create file in Downloads");
                }
            } else {
                // Android 9 and below: Use direct file access
                File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }
                
                File exportFile = new File(downloadsDir, fileName);
                
                try (FileOutputStream fileOutputStream = new FileOutputStream(exportFile, false)) {
                    // Write UTF-8 BOM first and flush to ensure it's written before other content
                    fileOutputStream.write(0xEF);
                    fileOutputStream.write(0xBB);
                    fileOutputStream.write(0xBF);
                    fileOutputStream.flush();
                    
                    // Now create writer and write content
                    try (OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {
                        // Write header
                        writer.write("Misspell,Correct,Always on?\n");

                        // Write entries
                        for (TextReplacementEntry entry : entriesToExport) {
                            if (entry != null && entry.getMisspell() != null && !entry.getMisspell().trim().isEmpty()) {
                                writer.write(entry.toCsv() + "\n");
                            }
                        }
                        writer.flush();
                    }
                }
                
                // Notify media scanner
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(exportFile);
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);
                
                Toast.makeText(this, getString(R.string.tr_saved_to_downloads, fileName), Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to export CSV", e);
            Toast.makeText(this, getString(R.string.tr_failed_export, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Share CSV file via share intent
     */
    private void shareCsv() {
        try {
            // Get current entries from adapter
            List<TextReplacementEntry> entriesToShare;
            if (adapter != null) {
                entriesToShare = adapter.getEntries();
            } else {
                entriesToShare = TextReplacementCsvManager.loadCsvFromStorage(this);
            }

            // Create filename with timestamp
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
            String timestamp = sdf.format(new java.util.Date());
            String fileName = "text_replacements_" + timestamp + ".csv";

            // Create temporary file in cache directory
            File cacheDir = getCacheDir();
            File shareFile = new File(cacheDir, fileName);

            // Write CSV to temporary file
            try (FileOutputStream fileOutputStream = new FileOutputStream(shareFile, false)) {
                // Write UTF-8 BOM first and flush to ensure it's written before other content
                fileOutputStream.write(0xEF);
                fileOutputStream.write(0xBB);
                fileOutputStream.write(0xBF);
                fileOutputStream.flush();
                
                // Now create writer and write content
                try (OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {
                    // Write header
                    writer.write("Misspell,Correct,Always on?\n");

                    // Write entries
                    for (TextReplacementEntry entry : entriesToShare) {
                        if (entry != null && entry.getMisspell() != null && !entry.getMisspell().trim().isEmpty()) {
                            writer.write(entry.toCsv() + "\n");
                        }
                    }
                    writer.flush();
                }
            }

            // Share the file using FileProvider
            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    shareFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv; charset=utf-8");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.tr_text_replacements_export));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, getString(R.string.tr_share_csv_file)));

        } catch (Exception e) {
            Log.e(TAG, "Failed to share CSV", e);
            Toast.makeText(this, getString(R.string.tr_failed_share_csv, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show confirmation dialog before uploading CSV file
     */
    private void showUploadConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tr_upload_csv_title)
                .setMessage(R.string.tr_upload_csv_message)
                .setPositiveButton(R.string.tr_yes_upload, (dialog, which) -> {
                    openFilePicker();
                })
                .setNegativeButton(R.string.tr_cancel, null)
                .show();
    }

    /**
     * Open file picker to select CSV file
     */
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // Only allow CSV files
        String[] mimeTypes = {"text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel"};
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        try {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.tr_select_csv_file)), REQUEST_CODE_PICK_CSV);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.tr_no_file_picker, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_PICK_CSV && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri fileUri = data.getData();
                importFromCsv(fileUri);
            }
        }
    }

    /**
     * Import text replacement data from CSV file
     */
    private void importFromCsv(Uri fileUri) {
        try {
            // Verify file type is CSV
            String mimeType = getContentResolver().getType(fileUri);
            if (mimeType != null && !mimeType.equals("text/csv") && 
                !mimeType.equals("text/comma-separated-values") && 
                !mimeType.equals("application/csv") &&
                !mimeType.equals("application/vnd.ms-excel")) {
                // Check file extension as fallback
                String fileName = getFileName(fileUri);
                if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
                    Toast.makeText(this, R.string.tr_please_select_csv, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // Read CSV file from URI
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                Toast.makeText(this, R.string.tr_failed_read_file, Toast.LENGTH_SHORT).show();
                return;
            }

            // Check for UTF-8 BOM and skip it if present
            java.io.PushbackInputStream pushbackInputStream = new java.io.PushbackInputStream(inputStream, 3);
            byte[] bom = new byte[3];
            int bytesRead = pushbackInputStream.read(bom, 0, 3);
            if (bytesRead == 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF) {
                // BOM found, skip it
            } else if (bytesRead > 0) {
                // No BOM or partial read, push back the bytes
                pushbackInputStream.unread(bom, 0, bytesRead);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(pushbackInputStream, StandardCharsets.UTF_8));
            List<TextReplacementEntry> importedEntries = new ArrayList<>();
            
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
                
                // Parse CSV line
                TextReplacementEntry entry = TextReplacementEntry.fromCsv(line);
                if (entry != null && entry.getMisspell() != null && !entry.getMisspell().trim().isEmpty()) {
                    importedEntries.add(entry);
                }
            }
            
            reader.close();
            inputStream.close();
            
            // Replace existing entries with imported ones
            if (importedEntries.isEmpty()) {
                Toast.makeText(this, R.string.tr_no_valid_entries, Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Update the entries list
            entries.clear();
            entries.addAll(importedEntries);
            
            // Save to storage
            TextReplacementCsvManager.saveCsvToStorage(this, entries);
            
            // Reload adapter
            if (adapter != null) {
                adapter = new TextReplacementAdapter(entries);
                adapter.setCounterVisible(getCounterVisiblePreference());
                recyclerView.setAdapter(adapter);
            } else {
                adapter = new TextReplacementAdapter(entries);
                adapter.setCounterVisible(getCounterVisiblePreference());
                recyclerView.setAdapter(adapter);
            }

            // Reload text replacement manager
            TextReplacementManager.getInstance(this).reload();

            Toast.makeText(this, getString(R.string.tr_imported_entries, importedEntries.size()), Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to import CSV", e);
            Toast.makeText(this, getString(R.string.tr_failed_import, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show confirmation dialog before deleting all entries
     */
    private void showDeleteAllConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tr_delete_all_title)
                .setMessage(R.string.tr_delete_all_message)
                .setPositiveButton(R.string.tr_delete_all_btn, (dialog, which) -> {
                    deleteAllEntries();
                })
                .setNegativeButton(R.string.tr_cancel, null)
                .show();
    }

    /**
     * Show confirmation dialog before deleting entries with counter 0
     */
    private void showDeleteUnusedConfirmationDialog() {
        int count = countUnusedEntries();
        if (count == 0) {
            Toast.makeText(this, R.string.tr_no_unused_entries, Toast.LENGTH_SHORT).show();
            return;
        }
        String message = count == 1 ? getString(R.string.tr_delete_unused_message_one) : getString(R.string.tr_delete_unused_message_many, count);
        new AlertDialog.Builder(this)
                .setTitle(R.string.tr_delete_unused_title)
                .setMessage(message)
                .setPositiveButton(R.string.tr_delete_btn, (dialog, which) -> {
                    deleteUnusedEntries();
                })
                .setNegativeButton(R.string.tr_cancel, null)
                .show();
    }

    /**
     * Count entries with counter 0 (excluding empty placeholder rows).
     */
    private int countUnusedEntries() {
        if (entries == null) return 0;
        int count = 0;
        for (TextReplacementEntry entry : entries) {
            if (entry != null && entry.getCounter() == 0) {
                String m = entry.getMisspell();
                String c = entry.getCorrect();
                if ((m != null && !m.trim().isEmpty()) || (c != null && !c.trim().isEmpty())) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Delete all entries that have counter 0, then reload.
     */
    private void deleteUnusedEntries() {
        try {
            if (entries == null) {
                return;
            }
            List<TextReplacementEntry> toKeep = new ArrayList<>();
            for (TextReplacementEntry entry : entries) {
                if (entry == null) continue;
                if (entry.getCounter() > 0) {
                    toKeep.add(entry);
                }
                // Skip entries with counter == 0 (they are "unused")
            }
            entries.clear();
            entries.addAll(toKeep);
            TextReplacementCsvManager.saveCsvToStorage(this, adapter != null ? adapter.getEntries() : entries);
            TextReplacementManager.getInstance(this).reload();
            loadData();
            Toast.makeText(this, R.string.tr_unused_deleted, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete unused entries", e);
            Toast.makeText(this, R.string.tr_failed_delete_unused, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show confirmation dialog before clearing all counters
     */
    private void showClearCounterConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tr_clear_counters_title)
                .setMessage(R.string.tr_clear_counters_message)
                .setPositiveButton(R.string.tr_clear_btn, (dialog, which) -> {
                    clearAllCounters();
                })
                .setNegativeButton(R.string.tr_cancel, null)
                .show();
    }

    /**
     * Clear all counters and reload the list
     */
    private void clearAllCounters() {
        try {
            if (entries == null) {
                return;
            }
            for (TextReplacementEntry entry : entries) {
                if (entry != null) {
                    entry.setCounter(0);
                }
            }
            List<TextReplacementEntry> toSave = adapter != null ? adapter.getEntries() : entries;
            TextReplacementCsvManager.saveCsvToStorage(this, toSave);
            TextReplacementManager.getInstance(this).reload();
            loadData();
            Toast.makeText(this, R.string.tr_counters_cleared, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear counters", e);
            Toast.makeText(this, R.string.tr_failed_clear_counters, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Delete all text replacement entries
     */
    private void deleteAllEntries() {
        try {
            // Clear all entries
            if (entries != null) {
                entries.clear();
            }
            
            // Save empty list to CSV
            List<TextReplacementEntry> emptyList = new ArrayList<>();
            TextReplacementCsvManager.saveCsvToStorage(this, emptyList);
            
            // Reload adapter
            if (adapter != null) {
                adapter = new TextReplacementAdapter(entries);
                adapter.setCounterVisible(getCounterVisiblePreference());
                recyclerView.setAdapter(adapter);
            }

            // Reload text replacement manager
            TextReplacementManager.getInstance(this).reload();

            Toast.makeText(this, R.string.tr_all_entries_deleted, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete all entries", e);
            Toast.makeText(this, getString(R.string.tr_failed_delete_all, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Get file name from URI
     */
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get file name", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
    }

    /**
     * Show search dialog
     */
    private void showSearchDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_search, null);
        
        EditText editSearch = dialogView.findViewById(R.id.edit_search);
        Button buttonFindNext = dialogView.findViewById(R.id.button_find_next);
        Button buttonFindPrevious = dialogView.findViewById(R.id.button_find_previous);
        Button buttonClose = dialogView.findViewById(R.id.button_close);
        TextView textSearchStatus = dialogView.findViewById(R.id.text_search_status);
        
        // Create dialog
        searchDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        // Setup close button
        if (buttonClose != null) {
            buttonClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (searchDialog != null) {
                        searchDialog.dismiss();
                    }
                }
            });
        }
        
        // Make dialog window transparent and position it above keyboard
        if (searchDialog.getWindow() != null) {
            searchDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Position dialog right above keyboard
            android.view.WindowManager.LayoutParams params = searchDialog.getWindow().getAttributes();
            params.gravity = android.view.Gravity.BOTTOM;
            params.verticalMargin = 0f; // At the bottom, will be above keyboard
            searchDialog.getWindow().setAttributes(params);
            // Adjust for keyboard - dialog will appear above it
            searchDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | 
                                                      WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        
        // Setup search text watcher
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                currentSearchTerm = s.toString().trim();
                performSearch();
                updateSearchStatus(textSearchStatus);
            }
        });
        
        // Setup Find Next button
        buttonFindNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findNext();
                updateSearchStatus(textSearchStatus);
            }
        });
        
        // Setup Find Previous button
        buttonFindPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                findPrevious();
                updateSearchStatus(textSearchStatus);
            }
        });
        
        // Handle Enter key in search field
        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            findNext();
            updateSearchStatus(textSearchStatus);
            return true;
        });
        
        searchDialog.show();
        
        // Focus on search field
        editSearch.requestFocus();
    }
    
    /**
     * Perform search and find all matches
     */
    private void performSearch() {
        matchPositions.clear();
        currentMatchIndex = -1;
        
        if (currentSearchTerm.isEmpty() || adapter == null || entries == null) {
            return;
        }
        
        String searchTermLower = currentSearchTerm.toLowerCase();
        
        // Search through all entries (misspell and correct columns)
        for (int i = 0; i < entries.size(); i++) {
            TextReplacementEntry entry = entries.get(i);
            if (entry == null) {
                continue;
            }
            
            String misspell = entry.getMisspell() != null ? entry.getMisspell().toLowerCase() : "";
            String correct = entry.getCorrect() != null ? entry.getCorrect().toLowerCase() : "";
            
            // Check for exact match in either field
            if (misspell.equals(searchTermLower) || correct.equals(searchTermLower)) {
                matchPositions.add(i);
            }
        }
        
        // If matches found, go to first match
        if (!matchPositions.isEmpty()) {
            currentMatchIndex = 0;
            scrollToMatch(matchPositions.get(0));
        }
    }
    
    /**
     * Find next match
     */
    private void findNext() {
        if (matchPositions.isEmpty()) {
            performSearch();
            return;
        }
        
        if (currentMatchIndex < 0) {
            currentMatchIndex = 0;
        } else {
            currentMatchIndex = (currentMatchIndex + 1) % matchPositions.size();
        }
        
        scrollToMatch(matchPositions.get(currentMatchIndex));
    }
    
    /**
     * Find previous match
     */
    private void findPrevious() {
        if (matchPositions.isEmpty()) {
            performSearch();
            return;
        }
        
        if (currentMatchIndex < 0) {
            currentMatchIndex = matchPositions.size() - 1;
        } else {
            currentMatchIndex = (currentMatchIndex - 1 + matchPositions.size()) % matchPositions.size();
        }
        
        scrollToMatch(matchPositions.get(currentMatchIndex));
    }
    
    /**
     * Scroll to match position in RecyclerView
     */
    private void scrollToMatch(int entryIndex) {
        if (recyclerView == null || adapter == null) {
            return;
        }
        
        // Header is now separate, so entryIndex directly maps to RecyclerView position
        if (entryIndex >= 0 && entryIndex < adapter.getItemCount()) {
            // Use smoothScrollToPosition for better UX, or scrollToPosition for immediate scroll
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(entryIndex, 0);
            } else {
                recyclerView.scrollToPosition(entryIndex);
            }
        }
    }
    
    /**
     * Update search status text
     */
    private void updateSearchStatus(TextView statusView) {
        if (statusView == null) {
            return;
        }
        
        if (currentSearchTerm.isEmpty()) {
            statusView.setText("");
            return;
        }
        
        if (matchPositions.isEmpty()) {
            statusView.setText(getString(R.string.tr_no_matches_found));
        } else {
            int displayIndex = currentMatchIndex >= 0 ? currentMatchIndex + 1 : 0;
            statusView.setText(getString(R.string.tr_search_status_of, displayIndex, matchPositions.size()));
        }
    }
}

