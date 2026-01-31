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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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
import java.util.List;

import rkr.simplekeyboard.inputmethod.R;

/**
 * Activity for managing text replacement entries.
 */
public class TextReplacementActivity extends AppCompatActivity {
    private static final String TAG = "TextReplacementActivity";
    private static final int REQUEST_CODE_PICK_CSV = 1001;
    private RecyclerView recyclerView;
    private TextReplacementAdapter adapter;
    private List<TextReplacementEntry> entries;

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

            // Setup delete button
            Button deleteButton = findViewById(R.id.button_delete);
            if (deleteButton != null) {
                deleteButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (adapter != null && adapter.hasSelectedEntries()) {
                            adapter.deleteSelectedEntries();
                            // Save after deletion
                            saveData();
                            // Refresh adapter without full reload
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
                
                // Setup long press to delete all entries
                deleteButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showDeleteAllConfirmationDialog();
                        return true;
                    }
                });
            }

            // Load data
            loadData();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            finish();
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
     * Load data from CSV file
     */
    private void loadData() {
        try {
            // Initialize default CSV if needed
            TextReplacementCsvManager.initializeDefaultCsv(this);
            
            // Load from storage
            entries = TextReplacementCsvManager.loadCsvFromStorage(this);
            
            // Setup adapter
            adapter = new TextReplacementAdapter(entries);
            recyclerView.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load data", e);
            // Create empty list on error
            entries = new java.util.ArrayList<>();
            adapter = new TextReplacementAdapter(entries);
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
                    
                    Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
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
                
                Toast.makeText(this, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to export CSV", e);
            Toast.makeText(this, "Failed to export: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Text Replacements Export");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share CSV File"));

        } catch (Exception e) {
            Log.e(TAG, "Failed to share CSV", e);
            Toast.makeText(this, "Failed to share CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show confirmation dialog before uploading CSV file
     */
    private void showUploadConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Upload CSV File")
                .setMessage("This will overwrite all current text replacement data. Are you sure you want to continue?")
                .setPositiveButton("Yes, Upload", (dialog, which) -> {
                    openFilePicker();
                })
                .setNegativeButton("Cancel", null)
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
            startActivityForResult(Intent.createChooser(intent, "Select CSV File"), REQUEST_CODE_PICK_CSV);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, "Please select a CSV file", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // Read CSV file from URI
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "No valid entries found in CSV file", Toast.LENGTH_SHORT).show();
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
                recyclerView.setAdapter(adapter);
            } else {
                adapter = new TextReplacementAdapter(entries);
                recyclerView.setAdapter(adapter);
            }
            
            // Reload text replacement manager
            TextReplacementManager.getInstance(this).reload();
            
            Toast.makeText(this, "Imported " + importedEntries.size() + " entries", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to import CSV", e);
            Toast.makeText(this, "Failed to import CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show confirmation dialog before deleting all entries
     */
    private void showDeleteAllConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Entries")
                .setMessage("This will permanently delete ALL text replacement entries. This action cannot be undone. Are you sure?")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    deleteAllEntries();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                recyclerView.setAdapter(adapter);
            }
            
            // Reload text replacement manager
            TextReplacementManager.getInstance(this).reload();
            
            Toast.makeText(this, "All entries deleted", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete all entries", e);
            Toast.makeText(this, "Failed to delete all entries: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
}

