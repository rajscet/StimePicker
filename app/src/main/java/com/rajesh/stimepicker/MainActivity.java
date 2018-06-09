package com.rajesh.stimepicker;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import net.simonvt.numberpicker.TimePicker;
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener;
import net.yslibrary.android.keyboardvisibilityevent.Unregistrar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    Unregistrar mUnregistrar;
    TimePicker timePicker;
     Calendar calendar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timePicker = findViewById(R.id.time_picker);
         calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        timePicker.setCalendar(calendar);

        mUnregistrar = KeyboardVisibilityEvent.registerEventListener(this, new KeyboardVisibilityEventListener() {
            @Override
            public void onVisibilityChanged(boolean isOpen) {
                if (isOpen) {

                } else {
                    timePicker.onEditingDone();
                }
            }
        });
        timePicker.setEventListener(new TimePicker.ICustomEventListener() {
            @Override
            public void isTimeEditOpened(boolean isTimeEditOpened) {

                if (isTimeEditOpened) {


                } else {

                }
            }
        });

        timePicker.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
            @Override
            public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
                calendar.set(Calendar.HOUR_OF_DAY,hourOfDay);
                calendar.set(Calendar.MINUTE,minute);
                timePicker.setCalendar(calendar);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUnregistrar.unregister();

    }

    public String getTime(long mills) {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a");
        Date date = new Date(mills);
        return sdf.format(date);
    }
}
