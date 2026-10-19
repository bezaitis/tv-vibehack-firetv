package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.Choreographer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Screensaver: the launcher's thin clock and date over a slow ambient glow, or over the theme's wallpapers when it
 * has any (one picture, or a slideshow that crossfades every wallpaper_seconds). Every minute the clock drifts to a
 * new spot and the glow shifts colour, so nothing burns in. No network: cheap enough for a 1 GB TV.
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private GradientDrawable glow;
    private int colorIndex;
    private int glowColor;

    /** Theme wallpapers behind the clock: bottom shows the current one, top fades the next one in. */
    private final List<File> photos = new ArrayList<>();
    private ImageView photoBottom, photoTop;
    private int photoIndex;
    private long photoMs;
    private boolean photosRunning;

    /** Bounce style: the clock glides diagonally, bouncing off the edges and changing colour on every bounce. */
    private static final float BOUNCE_DP_PER_SEC = 90f;
    private static final int[] BOUNCE_COLORS = {0xFFFF4D6D, 0xFF4DABFF, 0xFF6BE675, 0xFFFFD43B, 0xFFB57BFF, 0xFFFF9F43, 0xFF3BE8D8};
    private boolean bounce;
    private float bx, by, vx, vy;
    private long lastFrameNs;
    private int bounceColor;
    private final List<TextView> clockTexts = new ArrayList<>();
    private final List<Integer> clockAlphas = new ArrayList<>();
    private final Choreographer.FrameCallback bounceFrame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            stepBounce(now);
            if (bounce) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private final Runnable photoTick = new Runnable() {
        @Override
        public void run() {
            nextPhoto();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
        Config cfg = Config.load(this);
        bounce = "bounce".equals(cfg.screensaverStyle);
        if (bounce) {
            ambient.setVisibility(View.GONE);   // plain black, like the classic bouncing-logo screensaver
            collectTexts(content);
            bounceColor = random.nextInt(BOUNCE_COLORS.length);
            tintClock(BOUNCE_COLORS[bounceColor]);
        } else if (!"glow".equals(cfg.screensaverStyle)) {
            setupPhotos(ambient);
        }
    }

    private void collectTexts(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            clockTexts.add(t);
            clockAlphas.add(t.getCurrentTextColor() >>> 24);   // keep each line's own transparency
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectTexts(g.getChildAt(i));
        }
    }

    private void tintClock(int color) {
        for (int i = 0; i < clockTexts.size(); i++) {
            clockTexts.get(i).setTextColor((clockAlphas.get(i) << 24) | (color & 0x00FFFFFF));
        }
    }

    private void startBounce() {
        View parent = (View) content.getParent();
        float speed = BOUNCE_DP_PER_SEC * getResources().getDisplayMetrics().density;
        bx = Math.max(0, parent.getWidth() - content.getWidth()) * random.nextFloat();
        by = Math.max(0, parent.getHeight() - content.getHeight()) * random.nextFloat();
        vx = random.nextBoolean() ? speed : -speed;
        vy = random.nextBoolean() ? speed : -speed;
        content.setX(bx);
        content.setY(by);
        lastFrameNs = 0;
        Choreographer.getInstance().postFrameCallback(bounceFrame);
    }

    private void stepBounce(long now) {
        if (lastFrameNs == 0) { lastFrameNs = now; return; }
        float dt = Math.min(0.1f, (now - lastFrameNs) / 1e9f);   // a stalled frame never teleports the clock
        lastFrameNs = now;
        View parent = (View) content.getParent();
        float maxX = Math.max(0, parent.getWidth() - content.getWidth());
        float maxY = Math.max(0, parent.getHeight() - content.getHeight());
        bx += vx * dt;
        by += vy * dt;
        boolean hitX = false, hitY = false;
        if (bx <= 0) { bx = 0; vx = Math.abs(vx); hitX = true; }
        else if (bx >= maxX) { bx = maxX; vx = -Math.abs(vx); hitX = true; }
        if (by <= 0) { by = 0; vy = Math.abs(vy); hitY = true; }
        else if (by >= maxY) { by = maxY; vy = -Math.abs(vy); hitY = true; }
        content.setX(bx);
        content.setY(by);
        if (hitX || hitY) {
            bounceColor = (bounceColor + 1 + random.nextInt(BOUNCE_COLORS.length - 1)) % BOUNCE_COLORS.length;
            tintClock(BOUNCE_COLORS[bounceColor]);
        }
        if (hitX && hitY) celebrateCorner();
    }

    /** The moment everyone waits for: a perfect corner. A quick pop and a run through every colour. */
    private void celebrateCorner() {
        content.animate().cancel();
        content.setAlpha(1f);   // in case the corner came during the opening fade-in
        content.setScaleX(1f);
        content.setScaleY(1f);
        content.animate().scaleX(1.25f).scaleY(1.25f).setDuration(250).withEndAction(() ->
                content.animate().scaleX(1f).scaleY(1f).setDuration(400).start()).start();
        for (int i = 1; i <= BOUNCE_COLORS.length; i++) {
            final int c = BOUNCE_COLORS[(bounceColor + i) % BOUNCE_COLORS.length];
            handler.postDelayed(() -> tintClock(c), i * 120L);
        }
    }

    /** The theme's wallpapers, if it has any and hasn't switched them off; they replace the glow. */
    private void setupPhotos(View ambient) {
        Config cfg = Config.load(this);
        photos.addAll(cfg.wallpapers);
        photoMs = cfg.wallpaperSeconds * 1000L;
        if (photos.isEmpty()) return;
        photoIndex = random.nextInt(photos.size());   // don't always open on the same picture
        Bitmap first = decode(photos.get(photoIndex));
        if (first == null) return;
        ViewGroup root = (ViewGroup) ambient.getParent();
        photoBottom = newPhotoLayer();
        photoBottom.setImageBitmap(first);
        root.addView(photoBottom, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (photos.size() > 1) {
            photoTop = newPhotoLayer();
            photoTop.setAlpha(0f);
            root.addView(photoTop, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        ambient.setVisibility(View.GONE);
    }

    private ImageView newPhotoLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private void nextPhoto() {
        if (!photosRunning || photoTop == null) return;
        final int next = (photoIndex + 1) % photos.size();
        final File file = photos.get(next);
        new Thread(() -> {
            final Bitmap bmp = decode(file);
            handler.post(() -> {
                if (!photosRunning) return;
                photoIndex = next;
                if (bmp != null) {
                    photoTop.setImageBitmap(bmp);
                    photoTop.setAlpha(0f);
                    photoTop.animate().alpha(1f).setDuration(2000).withEndAction(() -> {
                        photoBottom.setImageBitmap(bmp);
                        photoTop.setAlpha(0f);
                        photoTop.setImageDrawable(null);
                    }).start();
                }
                handler.postDelayed(photoTick, photoMs);
            });
        }, "dream-photo").start();
    }

    /** Downsampled to the screen in RGB_565; null for a bad or oversized picture, never a crash. */
    private Bitmap decode(File file) {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        if (bounce) {
            content.post(() -> {
                startBounce();
                content.animate().alpha(1f).setDuration(1200).start();
            });
            return;
        }
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.postDelayed(tick, MOVE_MS);
        if (photoTop != null) {
            photosRunning = true;
            handler.postDelayed(photoTick, photoMs);
        }
    }

    @Override
    public void onDreamingStopped() {
        bounce = false;
        Choreographer.getInstance().removeFrameCallback(bounceFrame);
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(tick);
        photosRunning = false;
        handler.removeCallbacks(photoTick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        bounce = false;
        Choreographer.getInstance().removeFrameCallback(bounceFrame);
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(tick);
        photosRunning = false;
        handler.removeCallbacks(photoTick);
        super.onDetachedFromWindow();
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
