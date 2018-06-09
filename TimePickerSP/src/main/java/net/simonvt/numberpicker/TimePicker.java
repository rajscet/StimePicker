/*
 * Copyright (C) 2007 The Android Open Source Project
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

package net.simonvt.numberpicker;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * A view for selecting the time of day, in either 24 hour or AM/PM mode. The
 * hour, each minute digit, and AM/PM (if applicable) can be conrolled by
 * vertical spinners. The hour can be entered by keyboard input. Entering in two
 * digit hours can be accomplished by hitting two digits within a timeout of
 * about a second (e.g. '1' then '2' to select 12). The minutes can be entered
 * by entering single digits. Under AM/PM mode, the user can hit 'a', 'A", 'p'
 * or 'P' to pick. For a dialog using this view, see
 * {@link android.app.TimePickerDialog}.
 * <p>
 * See the <a href="{@docRoot}guide/topics/ui/controls/pickers.html">Pickers</a>
 * guide.
 * </p>
 */
//@Widget
public class TimePicker extends FrameLayout {
    private Calendar mCalendar;
    private static final boolean DEFAULT_ENABLED_STATE = true;

    private static final int HOURS_IN_HALF_DAY = 12;

    public interface ICustomEventListener {
        public void isTimeEditOpened(boolean isTimeEditOpened);
    }

    private ICustomEventListener mEventListener;

    public void setEventListener(ICustomEventListener mEventListener) {
        this.mEventListener = mEventListener;
    }

    /**
     * A no-op callback used in the constructor to avoid null checks later in
     * the code.
     */
    private static final OnTimeChangedListener NO_OP_CHANGE_LISTENER = new OnTimeChangedListener() {
        public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
        }
    };

    // state
    private boolean mIs24HourView;

    private boolean mIsAm;

    // ui components
    private final NumberPicker mHourSpinner;

    private final NumberPicker mMinuteSpinner;

    private final NumberPickerAMPM mAmPmSpinner;

    public final EditText mHourSpinnerInput;

    private final EditText mMinuteSpinnerInput;
    public final EditText mEditHour;
    public final EditText mEditMin;

    private final EditText mAmPmSpinnerInput;
    private InputMethodManager imm;
    private final TextView mDivider;

    // Note that the legacy implementation of the TimePicker is
    // using a button for toggling between AM/PM while the new
    // version uses a NumberPicker spinner. Therefore the code
    // accommodates these two cases to be backwards compatible.
    private final Button mAmPmButton;

    private final String[] mAmPmStrings;

    private boolean mIsEnabled = DEFAULT_ENABLED_STATE;

    // callbacks
    private OnTimeChangedListener mOnTimeChangedListener;

    private Calendar mTempCalendar;

    private Locale mCurrentLocale;

    /**
     * The callback interface used to indicate the time has been adjusted.
     */
    public interface OnTimeChangedListener {

        /**
         * @param view      The view associated with this listener.
         * @param hourOfDay The current hour.
         * @param minute    The current minute.
         */
        void onTimeChanged(TimePicker view, int hourOfDay, int minute);
    }

    public TimePicker(Context context) {
        this(context, null);
    }

    public TimePicker(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.timePickerStyle);
    }

    public TimePicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // initialization based on locale
        setCurrentLocale(Locale.getDefault());
        mCalendar = Calendar.getInstance();
        // process style attributes
        //TypedArray attributesArray = context.obtainStyledAttributes(
        //        attrs, R.styleable.TimePicker, defStyle, 0);
        //int layoutResourceId = attributesArray.getResourceId(
        //        R.styleable.TimePicker_internalLayout, R.layout.time_picker);
        //attributesArray.recycle();
        int layoutResourceId = R.layout.time_picker_holo;

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(layoutResourceId, this, true);

        imm = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        // hour
        mHourSpinner = findViewById(R.id.hour);
        mEditHour = findViewById(R.id.edit_hour);
        mEditMin = findViewById(R.id.edit_min);

        mHourSpinner.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker spinner, int oldVal, int newVal) {
                // updateInputState();
                if (!is24HourView()) {
                    if ((oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY)
                            || (oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1)) {
                        mIsAm = !mIsAm;
                        updateAmPmControl();
                    }
                }

                onTimeChanged();
            }
        });
        mHourSpinnerInput = mHourSpinner.findViewById(R.id.np__numberpicker_input);
        mHourSpinnerInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        mHourSpinnerInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mEditHour.setVisibility(View.VISIBLE);
                    mEditMin.setVisibility(View.VISIBLE);
                    mMinuteSpinner.setVisibility(View.INVISIBLE);
                    mHourSpinner.setVisibility(View.INVISIBLE);
                    mEditHour.setText(mHourSpinnerInput.getText().toString());
                    mEditMin.setText(mMinuteSpinnerInput.getText().toString());
                    mHourSpinner.clearFocus();
                    mEditHour.requestFocus();
                    mEditHour.setSelection(mEditHour.getText().length());
                    mEditHour.selectAll();
                    showKeyboard();
                    if (mEventListener != null) {
                        mEventListener.isTimeEditOpened(true);
                    }
                }

            }
        });


        // divider (only for the new widget style)
        mDivider = findViewById(R.id.divider);
        if (mDivider != null) {
            mDivider.setText(R.string.time_picker_separator);
        }

        // minute
        mMinuteSpinner = findViewById(R.id.minute);
        mMinuteSpinner.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mMinuteSpinner.clearFocus();
                mMinuteSpinnerInput.requestFocus();
            }
        });
        mMinuteSpinner.setMinValue(0);
        mMinuteSpinner.setMaxValue(59);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setFormatter(NumberPicker.getTwoDigitFormatter());
        mMinuteSpinner.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker spinner, int oldVal, int newVal) {
                updateInputState();
                int minValue = mMinuteSpinner.getMinValue();
                int maxValue = mMinuteSpinner.getMaxValue();
                if (oldVal == maxValue && newVal == minValue) {
                    int newHour = mHourSpinner.getValue() + 1;
                    if (!is24HourView() && newHour == HOURS_IN_HALF_DAY) {
                        mIsAm = !mIsAm;
                        updateAmPmControl();
                    }
                    mHourSpinner.setValue(newHour);
                } else if (oldVal == minValue && newVal == maxValue) {
                    int newHour = mHourSpinner.getValue() - 1;
                    if (!is24HourView() && newHour == HOURS_IN_HALF_DAY - 1) {
                        mIsAm = !mIsAm;
                        updateAmPmControl();
                    }
                    mHourSpinner.setValue(newHour);
                }
                onTimeChanged();
            }
        });
        mMinuteSpinnerInput = mMinuteSpinner.findViewById(R.id.np__numberpicker_input);
        mMinuteSpinnerInput.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        mMinuteSpinnerInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {

                if (hasFocus) {
                    mEditHour.setVisibility(View.VISIBLE);
                    mEditMin.setVisibility(View.VISIBLE);
                    mMinuteSpinner.setVisibility(View.INVISIBLE);
                    mHourSpinner.setVisibility(View.INVISIBLE);
                    mEditHour.setText(mHourSpinnerInput.getText().toString());
                    mEditMin.setText(mMinuteSpinnerInput.getText().toString());
                    mMinuteSpinnerInput.clearFocus();
                    mEditMin.requestFocus();
                    mEditMin.setSelection(mEditMin.getText().length());
                    mEditMin.selectAll();

                    showKeyboard();
                    if (mEventListener != null) {
                        mEventListener.isTimeEditOpened(true);
                    }
                }
            }
        });


        mEditMin.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onEditingDone();
                }
                return false;
            }
        });

        mEditMin.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mEditMin.setSelection(mEditMin.getText().length());
                    mEditMin.selectAll();

                } else {
                  //  Log.e("b", "" + getDisplayDateForRangeSelector(getCalendar().getTimeInMillis()));
                    onTimeChangedForEdit();
                }

            }
        });
        mEditHour.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mEditHour.setSelection(mEditHour.getText().length());
                    mEditHour.selectAll();

                } else {
               //     Log.e("c", "" + getDisplayDateForRangeSelector(getCalendar().getTimeInMillis()));
                    onTimeChangedForEdit();
                }
            }
        });

        mEditMin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().length() > 0) {
                    mMinuteSpinner.setValue(Integer.parseInt(s.toString()));

                    if (s.length() > 1) {
                        mEditMin.setSelection(mEditMin.getText().length());
                        mEditMin.selectAll();
                  //      Log.e("d", "" + getDisplayDateForRangeSelector(getCalendar().getTimeInMillis()));
                        onTimeChangedForEdit();
                    }

                }

            }
        });
        mEditHour.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {

                if (s.toString().length() > 0) {

                    mHourSpinner.setValue(Integer.parseInt(s.toString()));

                    final int digit = Integer.parseInt(s.toString());
                    if (digit >= 2 && digit <= 12) {
                        mEditHour.setSelection(mEditHour.getText().length());
                        mEditHour.selectAll();
                        mEditHour.clearFocus();
                        mEditMin.requestFocus();
                        imm.showSoftInput(mMinuteSpinnerInput,
                                InputMethodManager.SHOW_IMPLICIT);
                        onTimeChangedForEdit();
                    }
                    //onTimeChanged();

                }

            }
        });

        mEditHour.setFilters(new InputFilter[]{new InputFilterForMinMax("1", "12"), new InputFilter.LengthFilter(2)});
        mEditMin.setFilters(new InputFilter[]{new InputFilterForMinMax("0", "59"), new InputFilter.LengthFilter(2)});

        /* Get the localized am/pm strings and use them in the spinner */
        mAmPmStrings = new DateFormatSymbols().getAmPmStrings();

        // am/pm
        View amPmView = findViewById(R.id.amPm);
        if (amPmView instanceof Button) {
            mAmPmSpinner = null;
            mAmPmSpinnerInput = null;
            mAmPmButton = (Button) amPmView;
            mAmPmButton.setOnClickListener(new OnClickListener() {
                public void onClick(View button) {
                    button.requestFocus();

                    mIsAm = !mIsAm;
                    updateAmPmControl();
                    onTimeChanged();

                }
            });
        } else {
            mAmPmButton = null;
            mAmPmSpinner = (NumberPickerAMPM) amPmView;
            mAmPmSpinner.setMinValue(0);
            mAmPmSpinner.setMaxValue(1);
            mAmPmSpinner.setDisplayedValues(mAmPmStrings);
            mAmPmSpinner.setOnValueChangedListener(new NumberPickerAMPM.OnValueChangeListener() {

                @Override
                public void onValueChange(NumberPickerAMPM picker, int oldVal, int newVal) {

                    // updateInputState();
                    // picker.requestFocus();
                    mIsAm = !mIsAm;
                    updateAmPmControl();
                    onTimeChanged();
                }
            });
            mAmPmSpinnerInput = mAmPmSpinner.findViewById(R.id.np__numberpicker_input);
            mAmPmSpinnerInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        }

        // update controls to initial state
        updateHourControl();
        updateAmPmControl();

        setOnTimeChangedListener(NO_OP_CHANGE_LISTENER);

        // set to current time
        setCurrentHour(mTempCalendar.get(Calendar.HOUR_OF_DAY));
        setCurrentMinute(mTempCalendar.get(Calendar.MINUTE));

        if (!isEnabled()) {
            setEnabled(false);
        }


        setContentDescriptions();

        // If not explicitly specified this view is important for accessibility.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }



    public void onEditingDone() {

        hideKeyboard();
        mEditHour.setVisibility(View.INVISIBLE);
        mEditMin.setVisibility(View.INVISIBLE);
        mMinuteSpinner.setVisibility(View.VISIBLE);
        mHourSpinner.setVisibility(View.VISIBLE);
        onTimeChangedForEdit();
        if (mEventListener != null) {
            mEventListener.isTimeEditOpened(false);
        }

    }

    public void showKeyboard() {
        imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
        }
    }


    public void hideKeyboard() {
        imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mEditMin.getWindowToken(), 0);
        }
    }

    public void removeFoucs() {
        mMinuteSpinnerInput.clearFocus();
        mHourSpinnerInput.clearFocus();
        mAmPmSpinnerInput.clearFocus();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        if (mDivider != null) {
            mDivider.setEnabled(enabled);
        }
        mHourSpinner.setEnabled(enabled);
        if (mAmPmSpinner != null) {
            mAmPmSpinner.setEnabled(enabled);
        } else {
            mAmPmButton.setEnabled(enabled);
        }
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setCurrentLocale(newConfig.locale);
    }

    /**
     * Sets the current locale.
     *
     * @param locale The current locale.
     */
    private void setCurrentLocale(Locale locale) {
        if (locale.equals(mCurrentLocale)) {
            return;
        }
        mCurrentLocale = locale;
        mTempCalendar = Calendar.getInstance(locale);
    }

    /**
     * Used to save / restore state of time picker
     */
    private static class SavedState extends BaseSavedState {

        private final int mHour;

        private final int mMinute;

        private SavedState(Parcelable superState, int hour, int minute) {
            super(superState);
            mHour = hour;
            mMinute = minute;
        }

        private SavedState(Parcel in) {
            super(in);
            mHour = in.readInt();
            mMinute = in.readInt();
        }

        public int getHour() {
            return mHour;
        }

        public int getMinute() {
            return mMinute;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mHour);
            dest.writeInt(mMinute);
        }

        @SuppressWarnings({"unused", "hiding"})
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState, getCurrentHour(), getCurrentMinute());
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setCurrentHour(ss.getHour());
        setCurrentMinute(ss.getMinute());
    }

    /**
     * Set the callback that indicates the time has been adjusted by the user.
     *
     * @param onTimeChangedListener the callback, should not be null.
     */
    public void setOnTimeChangedListener(OnTimeChangedListener onTimeChangedListener) {
        mOnTimeChangedListener = onTimeChangedListener;
    }

    /**
     * @return The current hour in the range (0-23).
     */
    public Integer getCurrentHour() {
        int currentHour = mHourSpinner.getValue();

       /* if (is24HourView()) {
            return currentHour;
        } else if (mIsAm) {
            return currentHour % HOURS_IN_HALF_DAY;
        } else {
            return (currentHour % HOURS_IN_HALF_DAY) + HOURS_IN_HALF_DAY;
        }*/
        if (is24HourView()) {
            return currentHour;
        } else {
            int returnHour = 0;
            if (mIsAm) {

                if (currentHour == 12)
                    returnHour = 0;
                else
                    returnHour = currentHour;

            } else {

                if (currentHour == 12)
                    returnHour = 12;
                else
                    returnHour = currentHour + 12;

            }

            return returnHour;
        }

    }


    public void setAMPM(boolean isAMPM) {
        this.mIsAm = isAMPM;
    }

    /**
     * Set the current hour.
     */
    public void setCurrentHour(Integer currentHour) {
        // why was Integer used in the first place?
        if (currentHour == null || currentHour == getCurrentHour()) {
            return;
        }
        if (!is24HourView()) {
            // convert [0,23] ordinal to wall clock display
            if (currentHour >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                if (currentHour > HOURS_IN_HALF_DAY) {
                    currentHour = currentHour - HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                if (currentHour == 0) {
                    currentHour = HOURS_IN_HALF_DAY;
                }
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(currentHour);
        onTimeChanged();
    }

    /**
     * Set whether in 24 hour or AM/PM mode.
     *
     * @param is24HourView True = 24 hour mode. False = AM/PM.
     */
    public void setIs24HourView(Boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;
        // cache the current hour since spinner range changes
        int currentHour = getCurrentHour();
        updateHourControl();
        // set value after spinner range is updated
        setCurrentHour(currentHour);
        updateAmPmControl();
    }

    /**
     * @return true if this is in 24 hour view else false.
     */
    public boolean is24HourView() {
        return mIs24HourView;
    }

    /**
     * @return The current minute.
     */
    public Integer getCurrentMinute() {
        return mMinuteSpinner.getValue();
    }

    /**
     * Set the current minute (0-59).
     */
    public void setCurrentMinute(Integer currentMinute) {
        if (currentMinute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(currentMinute);
        onTimeChanged();
    }

    @Override
    public int getBaseline() {
        return mHourSpinner.getBaseline();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return true;
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);

        int flags = DateUtils.FORMAT_SHOW_TIME;
        if (mIs24HourView) {
            flags |= DateUtils.FORMAT_24HOUR;
        } else {
            flags |= DateUtils.FORMAT_12HOUR;
        }
        mTempCalendar.set(Calendar.HOUR_OF_DAY, getCurrentHour());
        mTempCalendar.set(Calendar.MINUTE, getCurrentMinute());
        String selectedDateUtterance = DateUtils.formatDateTime(getContext(),
                mTempCalendar.getTimeInMillis(), flags);
        event.getText().add(selectedDateUtterance);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(TimePicker.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(TimePicker.class.getName());
    }

    private void updateHourControl() {
        if (is24HourView()) {
            mHourSpinner.setMinValue(0);
            mHourSpinner.setMaxValue(23);
            mHourSpinner.setFormatter(NumberPicker.getTwoDigitFormatter());
        } else {
            mHourSpinner.setMinValue(1);
            mHourSpinner.setMaxValue(12);
            mHourSpinner.setFormatter(null);
        }
    }

    private void updateAmPmControl() {
        if (is24HourView()) {
            if (mAmPmSpinner != null) {
                mAmPmSpinner.setVisibility(View.GONE);
            } else {
                mAmPmButton.setVisibility(View.GONE);
            }
        } else {
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            if (mAmPmSpinner != null) {
                mAmPmSpinner.setValue(index);
                mAmPmSpinner.setVisibility(View.VISIBLE);
            } else {
                mAmPmButton.setText(mAmPmStrings[index]);
                mAmPmButton.setVisibility(View.VISIBLE);
            }
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    private void onTimeChangedForEdit() {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        if (mOnTimeChangedListener != null) {

            final int currentHour = mHourSpinner.getValue();
            int returnHour = 0;
            if (getDisplayDateForRangeSelector(getCalendar().getTimeInMillis()).toLowerCase().contains("am")) {
                if (currentHour == 12)
                    returnHour = 0;
                else
                    returnHour = currentHour;
            } else {

                if (currentHour == 12)
                    returnHour = 12;
                else
                    returnHour = currentHour + 12;


            }
            mOnTimeChangedListener.onTimeChanged(this, returnHour, getCurrentMinute());
        }
    }

    private void onTimeChanged() {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        if (mOnTimeChangedListener != null) {

            mOnTimeChangedListener.onTimeChanged(this, getCurrentHour(), getCurrentMinute());
        }
    }


    private void setContentDescriptions() {
        if (true)
            return; // This is never reached anyway, backport doesn't have increment/decrement buttons
        // Minute
        trySetContentDescription(mMinuteSpinner, R.id.np__increment,
                R.string.time_picker_increment_minute_button);
        trySetContentDescription(mMinuteSpinner, R.id.np__decrement,
                R.string.time_picker_decrement_minute_button);
        // Hour
        trySetContentDescription(mHourSpinner, R.id.np__increment,
                R.string.time_picker_increment_hour_button);
        trySetContentDescription(mHourSpinner, R.id.np__decrement,
                R.string.time_picker_decrement_hour_button);
        // AM/PM
        if (mAmPmSpinner != null) {
            trySetContentDescription(mAmPmSpinner, R.id.np__increment,
                    R.string.time_picker_increment_set_pm_button);
            trySetContentDescription(mAmPmSpinner, R.id.np__decrement,
                    R.string.time_picker_decrement_set_am_button);
        }
    }

    private void trySetContentDescription(View root, int viewId, int contDescResId) {
        View target = root.findViewById(viewId);
        if (target != null) {
            target.setContentDescription(getContext().getString(contDescResId));
        }
    }

    public Calendar getCalendar() {
        return this.mCalendar;
    }

    private void updateTime() {

        mHourSpinner.setValue(mCalendar.get(Calendar.HOUR));
        mMinuteSpinner.setValue(mCalendar.get(Calendar.MINUTE));
        mAmPmSpinner.setValue(mCalendar.get(Calendar.AM_PM));
    }

    public void setCalendar(Calendar calendar) {
        this.mCalendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY));
        this.mCalendar.set(Calendar.MINUTE, calendar.get(Calendar.MINUTE));
        this.mCalendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND));
        this.mCalendar.set(Calendar.MILLISECOND, calendar.get(Calendar.MILLISECOND));
        this.mCalendar.set(Calendar.DATE, calendar.get(Calendar.DATE));
        this.mCalendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH));
        this.mCalendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR));


        updateTime();
    }

    private void updateInputState() {

        // Make sure that if the user changes the value and the IME is active
        // for one of the inputs if this widget, the IME is closed. If the user
        // changed the value via the IME and there is a next input the IME will
        // be shown, otherwise the user chose another means of changing the
        // value and having the IME up makes no sense.
        //InputMethodManager inputMethodManager = InputMethodManager.peekInstance();

        /*InputMethodManager inputMethodManager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            if (inputMethodManager.isActive(mHourSpinnerInput)) {
                mHourSpinnerInput.clearFocus();
                inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            } else if (inputMethodManager.isActive(mMinuteSpinnerInput)) {
                mMinuteSpinnerInput.clearFocus();
                inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            } else if (inputMethodManager.isActive(mAmPmSpinnerInput)) {
              //
                  mAmPmSpinnerInput.clearFocus();
                inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            }

        }*/
        InputMethodManager inputMethodManager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        mHourSpinnerInput.clearFocus();
        inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);

        mMinuteSpinnerInput.clearFocus();
        inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);

        mAmPmSpinnerInput.clearFocus();
        inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);

    }

    public static String getDisplayDateForRangeSelector(long timeInMills) {

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a");
        Calendar defaultCalendar = Calendar.getInstance();
        defaultCalendar.setTimeInMillis(timeInMills);
        return sdf.format(defaultCalendar.getTimeInMillis());
    }



}
