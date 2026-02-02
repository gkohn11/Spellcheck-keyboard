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

package com.gkohn11.spellcheckkeyboard.latin;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gkohn11.spellcheckkeyboard.R;

/**
 * Suggestion bar that appears above the keyboard to show text replacement suggestions.
 */
public class TextReplacementSuggestionBar extends LinearLayout {
    Button mScanButton; // Scan button on the left
    View mScanDivider; // Divider after scan button
    TextView mOriginalWordText; // Package private for LatinIME to access
    EditText mCorrectionText; // Package private for LatinIME to access - now EditText for CSV input
    private String mCurrentOriginalWord;
    private String mCurrentSuggestion;
    private boolean mIsAutoReplace; // Track if current suggestion is auto-replace
    private boolean mIsEditableMode = false; // Track if correction box is in editable mode
    private boolean mIsScanMode = false; // Track if we're in scan mode
    private OnSuggestionClickListener mListener;
    private OnCsvInputListener mCsvInputListener;
    private OnScanClickListener mScanListener;

    public interface OnSuggestionClickListener {
        void onOriginalWordClicked(); // Called when user clicks the incorrect word (left side)
        void onCorrectionClicked(String suggestion); // Called when user clicks the correct word (right side)
    }
    
    public interface OnCsvInputListener {
        void onCsvInput(String csvLine); // Called when user enters CSV format in the correction box
    }
    
    public interface OnScanClickListener {
        void onScanClicked(); // Called when user clicks the scan button
        void onNextMisspelling(); // Called when user wants to go to next misspelling
        void onPreviousMisspelling(); // Called when user wants to go to previous misspelling
    }

    public TextReplacementSuggestionBar(Context context) {
        super(context);
        init();
    }

    public TextReplacementSuggestionBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TextReplacementSuggestionBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setVisibility(VISIBLE); // Always visible, but empty when no suggestions
        // Background color will be set by LatinIME to match keyboard theme
        // Default to a neutral color until theme is applied
        setBackgroundColor(0xFFE0E0E0);
        
        // Create scan button (leftmost)
        mScanButton = new Button(getContext());
        mScanButton.setText("🔍"); // Magnifying glass emoji for scan
        mScanButton.setTextSize(14);
        mScanButton.setPadding(12, 8, 12, 8);
        mScanButton.setMinWidth(0);
        mScanButton.setMinimumWidth(0);
        mScanButton.setBackgroundColor(0x40000000); // Semi-transparent background
        mScanButton.setTextColor(0xFF000000);
        mScanButton.setClickable(true);
        mScanButton.setFocusable(true);
        mScanButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (mScanListener != null) {
                    if (mIsScanMode) {
                        // If already in scan mode, go to next misspelling
                        mScanListener.onNextMisspelling();
                    } else {
                        // Start scan mode
                        mScanListener.onScanClicked();
                    }
                }
            }
        });
        // Long press to go to previous misspelling
        mScanButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (mIsScanMode && mScanListener != null) {
                    mScanListener.onPreviousMisspelling();
                }
                return true;
            }
        });
        
        // Create divider view
        View divider = new View(getContext());
        divider.setBackgroundColor(0x40000000); // Semi-transparent black
        
        // Original word text (left side) - clickable to add space
        mOriginalWordText = new TextView(getContext());
        mOriginalWordText.setPadding(16, 12, 16, 12);
        mOriginalWordText.setTextSize(16);
        mOriginalWordText.setTextColor(0xFF000000);
        mOriginalWordText.setVisibility(VISIBLE);
        mOriginalWordText.setGravity(android.view.Gravity.CENTER);
        mOriginalWordText.setClickable(true);
        mOriginalWordText.setFocusable(true);
        mOriginalWordText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (mListener != null) {
                    mListener.onOriginalWordClicked();
                }
            }
        });
        
        // Correction text (right side) - EditText for CSV input when no correction is shown
        mCorrectionText = new EditText(getContext());
        mCorrectionText.setPadding(16, 12, 16, 12);
        mCorrectionText.setTextSize(16);
        mCorrectionText.setTextColor(0xFF000000);
        mCorrectionText.setVisibility(VISIBLE);
        mCorrectionText.setGravity(android.view.Gravity.CENTER);
        mCorrectionText.setBackground(null); // No default EditText background
        mCorrectionText.setSingleLine(true);
        mCorrectionText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        
        // Handle clicks - if showing a correction, replace word; if editable, focus for input
        mCorrectionText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (!mIsEditableMode && mCurrentSuggestion != null && !mCurrentSuggestion.isEmpty()) {
                    // Showing a correction - replace word
                    if (mListener != null) {
                        mListener.onCorrectionClicked(mCurrentSuggestion);
                    }
                } else if (mIsEditableMode) {
                    // Editable mode - request focus for input
                    mCorrectionText.requestFocus();
                    // Show keyboard if not already shown
                    android.view.inputmethod.InputMethodManager imm = 
                        (android.view.inputmethod.InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(mCorrectionText, 0);
                    }
                }
            }
        });
        
        // Handle focus changes to ensure proper cursor visibility
        mCorrectionText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && mIsEditableMode) {
                    // When focused, ensure cursor is visible and at the end
                    mCorrectionText.setCursorVisible(true);
                    int length = mCorrectionText.getText().length();
                    mCorrectionText.setSelection(length);
                }
            }
        });
        
        // CSV processing is handled on Enter key press in LatinIME.handleInputToSuggestionBar()
        // The OnEditorActionListener is not needed since we intercept keyboard input directly
        
        // Prevent touch events from propagating to prevent navigation
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isShowing()) {
                    // Let child views handle their own clicks, but consume the event
                    return false; // Don't consume - let child views handle it
                }
                // When not showing, still consume events to prevent clicks through to background
                return true;
            }
        });
        
        // Add scan button (leftmost, fixed width)
        LinearLayout.LayoutParams scanButtonParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        scanButtonParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        mScanButton.setMinimumHeight(40);
        mScanButton.setMinimumWidth(50);
        addView(mScanButton, scanButtonParams);
        
        // Add small divider after scan button
        mScanDivider = new View(getContext());
        mScanDivider.setBackgroundColor(0x40000000);
        LinearLayout.LayoutParams scanDividerParams = new LinearLayout.LayoutParams(
                2, LayoutParams.MATCH_PARENT);
        scanDividerParams.setMargins(0, 8, 0, 8);
        mScanDivider.setLayoutParams(scanDividerParams);
        addView(mScanDivider);
        
        // Add original word text (left, weight 1)
        LinearLayout.LayoutParams originalParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1.0f);
        originalParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        mOriginalWordText.setMinimumHeight(40); // Thinner banner
        addView(mOriginalWordText, originalParams);
        
        // Add divider
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                2, LayoutParams.MATCH_PARENT);
        dividerParams.setMargins(0, 8, 0, 8);
        divider.setLayoutParams(dividerParams);
        addView(divider);
        
        // Add correction text (right, weight 1)
        LinearLayout.LayoutParams correctionParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1.0f);
        correctionParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        mCorrectionText.setMinimumHeight(40); // Thinner banner
        addView(mCorrectionText, correctionParams);
    }

    public void setOnSuggestionClickListener(OnSuggestionClickListener listener) {
        mListener = listener;
    }
    
    public void setOnCsvInputListener(OnCsvInputListener listener) {
        mCsvInputListener = listener;
    }
    
    public void setOnScanClickListener(OnScanClickListener listener) {
        mScanListener = listener;
    }
    
    public void setScanMode(boolean isScanMode) {
        mIsScanMode = isScanMode;
        if (mScanButton != null) {
            if (isScanMode) {
                mScanButton.setText("→"); // Arrow to indicate next
                mScanButton.setBackgroundColor(0x60FFA500); // Orange highlight when active
            } else {
                mScanButton.setText("🔍");
                mScanButton.setBackgroundColor(0x40000000);
            }
        }
    }
    
    /**
     * Set scan button visibility based on setting
     */
    public void setScanButtonEnabled(boolean enabled) {
        if (mScanButton != null) {
            mScanButton.setVisibility(enabled ? VISIBLE : GONE);
        }
        if (mScanDivider != null) {
            mScanDivider.setVisibility(enabled ? VISIBLE : GONE);
        }
    }

    public void showSuggestion(String originalWord, String suggestion, boolean isAutoReplace) {
        if (originalWord != null && !originalWord.isEmpty()) {
            mCurrentOriginalWord = originalWord;
            mCurrentSuggestion = suggestion;
            mIsAutoReplace = isAutoReplace;
            // Set text for original word (left side)
            mOriginalWordText.setText(originalWord);
            mOriginalWordText.setVisibility(VISIBLE);
            
            // Set text for correction (right side) - can be null if no replacement
            if (suggestion != null && !suggestion.isEmpty()) {
                // Showing a correction - make non-editable, clickable
                mIsEditableMode = false;
                mCorrectionText.setText(suggestion);
                mCorrectionText.setFocusable(false);
                mCorrectionText.setClickable(true);
                mCorrectionText.setCursorVisible(false);
                mCorrectionText.setVisibility(VISIBLE);
            } else {
                // No suggestion - make editable for CSV input
                mIsEditableMode = true;
                mCorrectionText.setText("");
                mCorrectionText.setFocusable(true);
                mCorrectionText.setFocusableInTouchMode(true);
                mCorrectionText.setClickable(true);
                mCorrectionText.setCursorVisible(true);
                mCorrectionText.setHint("Misspell,correct");
                mCorrectionText.setVisibility(VISIBLE); // Keep visible but empty
            }
            
            // Update highlight based on auto-replace status
            updateHighlight();
            
            setVisibility(VISIBLE);
            // Force layout update
            requestLayout();
            invalidate();
        } else {
            hideSuggestion();
        }
    }
    
    /**
     * Update highlight on correction text based on auto-replace status
     */
    private void updateHighlight() {
        if (mIsAutoReplace && mCorrectionText != null && mCurrentSuggestion != null && !mCurrentSuggestion.isEmpty()) {
            // Get background color to determine highlight color
            android.graphics.drawable.Drawable bg = getBackground();
            if (bg instanceof android.graphics.drawable.ColorDrawable) {
                int bgColor = ((android.graphics.drawable.ColorDrawable) bg).getColor();
                boolean isDark = android.graphics.Color.red(bgColor) + 
                                android.graphics.Color.green(bgColor) + 
                                android.graphics.Color.blue(bgColor) < 384;
                // Use a more visible highlight color based on background
                int highlightColor = isDark ? 0x60FFFFFF : 0x40000000; // Light on dark, dark on light
                mCorrectionText.setBackgroundColor(highlightColor);
            } else {
                // Default highlight
                mCorrectionText.setBackgroundColor(0x40FFFFFF);
            }
        } else if (mCorrectionText != null) {
            mCorrectionText.setBackgroundColor(0x00000000); // Transparent
        }
    }

    public void hideSuggestion() {
        mCurrentOriginalWord = null;
        mCurrentSuggestion = null;
        mIsAutoReplace = false;
        mIsEditableMode = true; // Allow CSV input when no suggestion
        mOriginalWordText.setText(""); // Clear text but keep visible
        mCorrectionText.setText(""); // Clear text but keep visible
        mCorrectionText.setBackgroundColor(0x00000000); // Clear highlight
        mCorrectionText.setFocusable(true);
        mCorrectionText.setFocusableInTouchMode(true);
        mCorrectionText.setClickable(true);
        mCorrectionText.setCursorVisible(true);
        mCorrectionText.setHint("Misspell,correct");
        mOriginalWordText.setVisibility(VISIBLE); // Keep text view visible
        mCorrectionText.setVisibility(VISIBLE); // Keep text view visible
        setVisibility(VISIBLE); // Keep visible but empty
    }
    
    /**
     * Completely hide the banner (used when text replacement feature is disabled)
     */
    public void hideBanner() {
        mCurrentOriginalWord = null;
        mCurrentSuggestion = null;
        mIsAutoReplace = false;
        mOriginalWordText.setText("");
        mCorrectionText.setText("");
        mCorrectionText.setBackgroundColor(0x00000000);
        setVisibility(View.GONE); // Completely hide the banner
    }

    public boolean isShowing() {
        return mCurrentSuggestion != null && !mCurrentSuggestion.isEmpty();
    }
    
    /**
     * Check if the correction EditText is focused and in editable mode
     */
    public boolean isCorrectionTextFocused() {
        return mIsEditableMode && mCorrectionText != null && mCorrectionText.isFocused();
    }
    
    /**
     * Get the correction EditText for direct input handling
     */
    public EditText getCorrectionEditText() {
        return mCorrectionText;
    }
    
    /**
     * Set background color (called by LatinIME to match keyboard theme)
     */
    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
    }
}

