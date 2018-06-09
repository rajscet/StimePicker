package net.simonvt.numberpicker;

import android.text.InputFilter;
import android.text.Spanned;

public class InputFilterForMinMax implements InputFilter {

    private int min, max;

    public InputFilterForMinMax(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public InputFilterForMinMax(String min, String max) {
        this.min = Integer.parseInt(min);
        this.max = Integer.parseInt(max);
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        try {
            String temp;
            if (dstart == 0) {
                temp = source.toString();
            } else {
                temp = dest.toString() + source.toString();
            }
            if (source.toString().equalsIgnoreCase(".")) {
                return "";
            } else if (temp.toString().indexOf(",") != -1) {
                temp = temp.toString().substring(temp.toString().indexOf(",") + 1);
                if (temp.length() > 2) {
                    return "";
                }
            }

            int input;
            if (dstart == 0) {
                input = Integer.parseInt(source.toString().replace(",", ".").replace("€", ""));

            } else {
                input = Integer.parseInt(dest.toString().replace(",", ".").replace("€", "") + source.toString().replace(",", ".").replace("€", ""));
            }

            if (isInRange(min, max, input))
                return null;
        } catch (NumberFormatException nfe) {
        }
        return "";
    }

    private boolean isInRange(int a, int b, int c) {
        return b > a ? c >= a && c <= b : c >= b && c <= a;
    }

}

