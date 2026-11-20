package tv.harry.home;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
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

    /** Set by Home when it has already faded the blurred picture in, so this starts with it showing. */
    static boolean handoff;
    static boolean running;
    private static Bitmap blur;

    /** Today's blurred picture, or null. Same day-of-year pick as Home.loadWallpaper, so the two match. */
    static Bitmap blur(Context c) {
        if (blur == null) {
            try {
                int n = 0;
                for (String a : c.getAssets().list("")) if (a.startsWith("blur") && a.endsWith(".jpg")) n++;
                try (InputStream in = c.getAssets().open(
                        "blur" + Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % n + ".jpg")) {
                    blur = BitmapFactory.decodeStream(in);
                }
            } catch (Exception | OutOfMemoryError e) {
                // No image: clock on black.
            }
        }
        return blur;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setFullscreen(true);
        running = true;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setImageBitmap(blur(this));
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextColor(0xFFFFFFFF);
        clock.setTextSize(110);
        clock.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        clock.setShadowLayer(24, 0, 2, 0x66000000);
        root.addView(clock, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        setContentView(root);

        // The picture and the clock fade separately. Fading the root as a whole goes through a
        // full-screen offscreen layer, which this GPU can only do at half frame rate.
        int wait = 120; // clears the slow first frames of the new window
        if (!handoff) {
            // Started by the idle timer over some other app: fade the picture in from black here.
            // Alpha starts at 1, not 0, so the image uploads to the GPU while still invisible.
            bg.setImageAlpha(1);
            ObjectAnimator a = ObjectAnimator.ofInt(bg, "imageAlpha", 1, 255).setDuration(500);
            a.setStartDelay(300);
            a.start();
            wait = 800;
        }
        handoff = false;
        clock.setAlpha(0f);
        clock.animate().alpha(1f).setDuration(200).setStartDelay(wait).start();
    }

    @Override
    public void onDetachedFromWindow() {
        running = false;
        if (Home.live != null) Home.live.afterSaver();
        super.onDetachedFromWindow();
    }
}
