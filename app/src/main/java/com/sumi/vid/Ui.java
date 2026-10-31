package com.sumi.vid;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

class Ui {
  static int BG = Color.rgb(11, 16, 32),
      CARD = Color.rgb(24, 32, 51),
      GREEN = Color.rgb(167, 243, 107),
      MUTED = Color.rgb(151, 165, 190);

  static void configure(Activity a) {
    android.content.SharedPreferences prefs = AppSettings.prefs(a);
    boolean oled = prefs.getBoolean("oled", false);
    BG = oled ? Color.BLACK : Color.rgb(11, 16, 32);
    CARD = oled ? 0xFF171717 : Color.rgb(24, 32, 51);
    int[] accents = {0xFFA7F36B, 0xFF7BCBFF, 0xFFC1A7FF, 0xFFFFCB70};
    GREEN = accents[Math.max(0, Math.min(3, prefs.getInt("accent", 0)))];
    a.getWindow().setStatusBarColor(BG);
    a.getWindow().setNavigationBarColor(BG);
  }

  static void refreshButtons(View view) {
    if (view instanceof Button) {
      Button b = (Button) view;
      b.setTextColor(GREEN);
      b.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(0x33FFFFFF),
              bg(CARD, dp(view.getContext(), 12)),
              bg(Color.WHITE, dp(view.getContext(), 12))));
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int j = 0; j < group.getChildCount(); j++) refreshButtons(group.getChildAt(j));
    }
  }

  static int dp(android.content.Context c, int n) {
    return (int) (n * c.getResources().getDisplayMetrics().density);
  }

  static TextView text(Activity a, String s, int size, int color) {
    TextView v = new TextView(a);
    v.setText(s);
    v.setTextColor(color);
    v.setTextSize(size);
    v.setPadding(dp(a, 6), dp(a, 6), dp(a, 6), dp(a, 6));
    return v;
  }

  static GradientDrawable bg(int color, int r) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(r);
    return g;
  }

  static Button button(Activity a, String s, Runnable r) {
    Button b = new Button(a);
    b.setText(s);
    b.setTextColor(GREEN);
    b.setTextSize(12);
    b.setAllCaps(false);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setMinHeight(dp(a, 44));
    b.setMinimumHeight(dp(a, 44));
    b.setPadding(dp(a, 12), 0, dp(a, 12), 0);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF), bg(CARD, dp(a, 12)), bg(Color.WHITE, dp(a, 12))));
    b.setOnClickListener(v -> r.run());
    return b;
  }

  static void insets(View v) {
    if (android.os.Build.VERSION.SDK_INT >= 30)
      v.setOnApplyWindowInsetsListener(
          (view, i) -> {
            android.graphics.Insets x = i.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(x.left, x.top, x.right, x.bottom);
            return i;
          });
    else
      v.setOnApplyWindowInsetsListener(
          (view, i) -> {
            view.setPadding(
                i.getSystemWindowInsetLeft(),
                i.getSystemWindowInsetTop(),
                i.getSystemWindowInsetRight(),
                i.getSystemWindowInsetBottom());
            return i;
          });
  }
}
