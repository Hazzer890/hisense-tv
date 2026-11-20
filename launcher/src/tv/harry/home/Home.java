package tv.harry.home;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One-screen TV home: photo up top, app tiles on frosted glass below. No services, no network. */
public class Home extends Activity {

    /** Home-screen tiles, in order. Anything not installed is skipped. */
    static final List<String> PINNED = Arrays.asList(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "au.net.abc.iview",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.crunchyroll.crunchyroid",
            "com.plexapp.android");

    static final int COLS = 5, SIDE = 58, GAP = 9;          // dp
    static final int WHITE = 0xFFFFFFFF, GLASS = 0x33FFFFFF, INK = 0xFF111111;

    private FrameLayout root;
    private Bitmap wallpaper;
    private boolean showingAll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);
        wallpaper = loadWallpaper();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuilt on every return so installs, removals and renamed inputs show up. It is a few dozen views.
        showHome();
    }

    /** Home pressed while already on the home screen starts the screensaver. From an app it just comes here. */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (!hasWindowFocus()) return;
        if (showingAll) showHome();
        else start(new Intent().setClassName("com.android.systemui", "com.android.systemui.Somnambulator"));
    }

    @Override
    public void onBackPressed() {
        if (showingAll) showHome(); // Home is the bottom of the stack; Back never leaves it.
    }

    // ---- screens ----

    private void showHome() {
        showingAll = false;
        root.removeAllViews();
        addBackdrop(0);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextColor(WHITE);
        clock.setTextSize(22);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setShadowLayer(dp(6), 0, dp(1), 0x99000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        cp.setMargins(0, dp(30), dp(SIDE), 0);
        root.addView(clock, cp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(SIDE - GAP), 0, dp(SIDE - GAP), dp(30));
        unclip(col);

        GridLayout grid = grid(COLS);
        int w = tileWidth(COLS);
        View first = null;
        List<ResolveInfo> all = launchables();
        for (String pkg : PINNED) {
            for (ResolveInfo r : all) {
                if (r.activityInfo.packageName.equals(pkg)) {
                    View t = appTile(r, w);
                    if (first == null) first = t;
                    grid.addView(t);
                    break;
                }
            }
        }
        grid.addView(resumeTile(w));
        grid.addView(tile(new Glyph(Glyph.GEAR), "Settings", w,
                v -> start(new Intent(Settings.ACTION_SETTINGS))));
        grid.addView(tile(new Glyph(Glyph.CLOSE), "Close Apps", w, v -> closeApps()));
        col.addView(grid);

        LinearLayout pills = new LinearLayout(this);
        unclip(pills);
        for (final TvInputInfo in : inputs()) {
            final Intent view = new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(in.getId()));
            pills.addView(pill(String.valueOf(in.loadLabel(this)), v -> start(view)));
        }
        pills.addView(pill("All Apps", v -> showAll()));
        col.addView(pills);

        root.addView(col, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        if (first != null) first.requestFocus();
    }

    private void showAll() {
        showingAll = true;
        root.removeAllViews();
        addBackdrop(0xD9000000);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(SIDE - GAP), dp(36), dp(SIDE - GAP), 0);
        unclip(col);

        TextView title = text("All Apps", 26, WHITE);
        title.setPadding(dp(GAP), 0, 0, dp(14));
        col.addView(title);

        final PackageManager pm = getPackageManager();
        List<ResolveInfo> all = launchables();
        Collections.sort(all, (a, c) ->
                a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString()));
        GridLayout grid = grid(6);
        int w = tileWidth(6);
        View first = null;
        for (ResolveInfo r : all) {
            View t = appTile(r, w);
            if (first == null) first = t;
            grid.addView(t);
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        unclip(sv);
        sv.addView(grid);
        col.addView(sv);

        root.addView(col);
        if (first != null) first.requestFocus();
    }

    private void addBackdrop(int tint) {
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (wallpaper != null) bg.setImageBitmap(wallpaper);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        if (tint != 0) {
            View shade = new View(this);
            shade.setBackgroundColor(tint);
            root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    // ---- data ----

    /** One bundled photo per day, in rotation. Null (plain black) if none are bundled or decoding fails. */
    private Bitmap loadWallpaper() {
        try {
            List<String> names = new ArrayList<>();
            for (String n : getAssets().list("")) if (n.startsWith("bg") && n.endsWith(".jpg")) names.add(n);
            if (names.isEmpty()) return null;
            Collections.sort(names);
            String pick = names.get(Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % names.size());
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565; // 4 MB instead of 8 on a 1.4 GB TV
            try (InputStream in = getAssets().open(pick)) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Every app with a TV launcher entry, except this one. */
    private List<ResolveInfo> launchables() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> out = new ArrayList<>();
        for (ResolveInfo r : getPackageManager().queryIntentActivities(i, 0)) {
            if (!r.activityInfo.packageName.equals(getPackageName())) out.add(r);
        }
        return out;
    }

    /** HDMI and AV inputs, sorted by the names the TV already gives them. */
    private List<TvInputInfo> inputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.isPassthroughInput() && !in.isHidden(this)) out.add(in);
        }
        Collections.sort(out, (a, c) ->
                String.valueOf(a.loadLabel(this)).compareTo(String.valueOf(c.loadLabel(this))));
        return out;
    }

    /**
     * Ends the background processes of every launchable or user-installed app and reports the
     * memory freed. An app with a foreground service (music playing, say) is left alone by Android.
     */
    private void closeApps() {
        final ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final long before = freeMb(am);
        Set<String> targets = new HashSet<>();
        for (ResolveInfo r : launchables()) targets.add(r.activityInfo.packageName);
        for (ApplicationInfo a : getPackageManager().getInstalledApplications(0)) {
            if ((a.flags & ApplicationInfo.FLAG_SYSTEM) == 0) targets.add(a.packageName);
        }
        targets.remove(getPackageName());
        for (String pkg : targets) am.killBackgroundProcesses(pkg);
        // The kernel takes a moment to hand the memory back.
        root.postDelayed(() -> Toast.makeText(this,
                "Closed background apps · " + freeMb(am) + " MB free (was " + before + ")",
                Toast.LENGTH_LONG).show(), 1000);
    }

    private static long freeMb(ActivityManager am) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            // App vanished or refused the intent: stay on Home rather than crash the launcher.
        }
    }

    // ---- views ----

    private View appTile(final ResolveInfo r, int w) {
        PackageManager pm = getPackageManager();
        Drawable banner = r.activityInfo.loadBanner(pm);
        if (banner == null) banner = r.activityInfo.applicationInfo.loadBanner(pm);
        final String pkg = r.activityInfo.packageName, cls = r.activityInfo.name;
        return tile(banner, String.valueOf(r.loadLabel(pm)), w, v -> {
            // Remembered so the Resume tile can reopen whatever was watched last.
            getPreferences(MODE_PRIVATE).edit().putString("pkg", pkg).putString("cls", cls).apply();
            start(launchIntent(pkg, cls));
        });
    }

    private static Intent launchIntent(String pkg, String cls) {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(pkg, cls);
    }

    /**
     * Reopens the last app started from this launcher, which lands on that app's own
     * Continue Watching. Android gives a third-party launcher no way to read the exact title.
     */
    private View resumeTile(int w) {
        final String pkg = getPreferences(MODE_PRIVATE).getString("pkg", null);
        final String cls = getPreferences(MODE_PRIVATE).getString("cls", null);
        String app = null;
        try {
            PackageManager pm = getPackageManager();
            if (pkg != null) app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            // Last app was uninstalled: fall through to the empty state.
        }
        final boolean ready = app != null;
        return tile(new Glyph(Glyph.PLAY), ready ? "Resume " + app : "Resume", w, v -> {
            if (ready) start(launchIntent(pkg, cls));
            else Toast.makeText(this, "Nothing to resume yet", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * A rounded 16:9 card with its name underneath. Focus lifts the card (scale + shadow) and
     * reveals the name. With no banner the card is frosted glass with the name on it.
     */
    private View tile(Drawable banner, String name, int w, View.OnClickListener onClick) {
        final FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(banner != null ? 0xFF1C1C1E : GLASS);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        card.setClipToOutline(true);
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView t = text(name, 18, WHITE);
            t.setGravity(Gravity.CENTER);
            card.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }

        final TextView caption = text(banner != null ? name : "", 13, WHITE);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setAlpha(0f);
        caption.setShadowLayer(dp(4), 0, dp(1), 0xAA000000);

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        unclip(cell);
        cell.addView(card, new LinearLayout.LayoutParams(w, w * 9 / 16));
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(w, dp(22));
        capLp.topMargin = dp(10);
        cell.addView(caption, capLp);

        cell.setFocusable(true);
        cell.setOnClickListener(onClick);
        cell.setOnFocusChangeListener((v, has) -> {
            card.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f)
                    .translationZ(has ? dp(14) : 0).setDuration(140).start();
            caption.animate().alpha(has ? 1f : 0f).setDuration(140).start();
        });
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(-2, -2);
        lp.setMargins(dp(GAP), dp(8), dp(GAP), 0);
        cell.setLayoutParams(lp);
        return cell;
    }

    /** Small glass capsule that turns solid white when focused. */
    private View pill(String label, View.OnClickListener onClick) {
        final TextView t = text(label, 15, WHITE);
        t.setPadding(dp(22), dp(9), dp(22), dp(9));
        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(GLASS);
        bg.setCornerRadius(dp(40));
        t.setBackground(bg);
        t.setFocusable(true);
        t.setOnClickListener(onClick);
        t.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? WHITE : GLASS);
            t.setTextColor(has ? INK : WHITE);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).start();
        });
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(-2, -2);
        lp.setMargins(dp(GAP), dp(6), dp(GAP), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private GridLayout grid(int cols) {
        GridLayout g = new GridLayout(this);
        g.setColumnCount(cols);
        unclip(g);
        return g;
    }

    /** Card width that makes `cols` tiles exactly fill the row between the side margins. */
    private int tileWidth(int cols) {
        int usable = getResources().getDisplayMetrics().widthPixels - 2 * dp(SIDE - GAP);
        return usable / cols - 2 * dp(GAP);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Lets focused tiles grow and cast shadows past their parent's bounds. */
    private void unclip(ViewGroup g) {
        g.setClipChildren(false);
        g.setClipToPadding(false);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Tile art for the two built-in tiles: a colour gradient with a white symbol, drawn in code. */
    static final class Glyph extends Drawable {
        static final int PLAY = 0, GEAR = 1, CLOSE = 2;
        static final int[] TOP = {0xFF4CD08A, 0xFFA4A9B3, 0xFFFF7A6B};
        static final int[] BOTTOM = {0xFF1E9E5E, 0xFF5C616B, 0xFFD93A3A};
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glyph(int kind) {
            this.kind = kind;
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, b.top, 0, b.bottom, TOP[kind], BOTTOM[kind],
                    Shader.TileMode.CLAMP));
            c.drawRect(b, paint);
            paint.setShader(null);
            paint.setColor(0xFFFFFFFF);

            float cx = b.exactCenterX(), cy = b.exactCenterY(), u = b.height() / 10f;
            if (kind == PLAY) {
                // play triangle, nudged right so it looks centred
                Path p = new Path();
                p.moveTo(cx - 1.5f * u, cy - 2.2f * u);
                p.lineTo(cx + 2.3f * u, cy);
                p.lineTo(cx - 1.5f * u, cy + 2.2f * u);
                p.close();
                paint.setPathEffect(new CornerPathEffect(0.5f * u));
                c.drawPath(p, paint);
                paint.setPathEffect(null);
            } else if (kind == CLOSE) {
                // X: two crossed bars
                for (int i = 0; i < 2; i++) {
                    c.save();
                    c.rotate(i == 0 ? 45f : -45f, cx, cy);
                    c.drawRoundRect(cx - 2.6f * u, cy - 0.45f * u, cx + 2.6f * u, cy + 0.45f * u,
                            0.45f * u, 0.45f * u, paint);
                    c.restore();
                }
            } else {
                // gear: eight teeth around a ring
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(45f * i, cx, cy);
                    c.drawRoundRect(cx - 0.5f * u, cy - 2.5f * u, cx + 0.5f * u, cy - 1.5f * u,
                            0.15f * u, 0.15f * u, paint);
                    c.restore();
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(0.95f * u);
                c.drawCircle(cx, cy, 1.4f * u, paint);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
