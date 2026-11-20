package tv.harry.home;

import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

import java.io.InputStream;
import java.util.Calendar;

/** Screensaver: today's wallpaper fully blurred, clock in the middle. */
public class Saver extends DreamService {

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        try {
            // Same day-of-year pick as Home.loadWallpaper, so it matches the home screen.
            int n = 0;
            for (String a : getAssets().list("")) if (a.startsWith("blur") && a.endsWith(".jpg")) n++;
            try (InputStream in = getAssets().open("blur" + Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % n + ".jpg")) {
                ImageView bg = new ImageView(this);
                bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                bg.setImageBitmap(BitmapFactory.decodeStream(in));
                root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
            }
        } catch (Exception | OutOfMemoryError e) {
            // No image: clock on black.
        }

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextColor(0xFFFFFFFF);
        clock.setTextSize(110);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        clock.setShadowLayer(24, 0, 2, 0x66000000);
        root.addView(clock, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        setContentView(root);
    }
}
