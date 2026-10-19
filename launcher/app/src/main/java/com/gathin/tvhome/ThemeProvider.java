package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets adb write a theme into the launcher's external files directory on TVs where the shell cannot write to
 * /sdcard/Android/data (Fire OS 8). Used by fire/theme.sh:
 *
 *   content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.gathin.tvhome.theme/wallpaper-3.jpg < /data/local/tmp/wallpaper-3.jpg
 *   content delete --uri content://com.gathin.tvhome.theme/wallpapers        (every wallpaper*.jpg)
 *
 * Only theme files (config.json, wallpaper.jpg, wallpaper-N.jpg) can be written or deleted, and both read and write need WRITE_SECURE_SETTINGS, which
 * the adb shell holds and ordinary apps cannot get, so no other app on the TV can change the theme.
 */
public class ThemeProvider extends ContentProvider {

    private File target(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!isThemeFile(name)) {
            throw new FileNotFoundException("only " + Config.FILE_NAME + ", " + Config.WALLPAPER_NAME + " and wallpaper-N.jpg");
        }
        File dir = getContext().getExternalFilesDir(null);
        if (dir == null) throw new FileNotFoundException("no external files directory");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new FileNotFoundException("cannot create " + dir);
        return new File(dir, name);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = target(uri);
        int flags = mode.contains("w")
                ? ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(file, flags);
    }

    private static boolean isThemeFile(String name) {
        return name != null && (Config.FILE_NAME.equals(name) || Config.WALLPAPER_NAME.equals(name)
                || Config.WALLPAPER_SERIES.matcher(name).matches());
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if ("wallpapers".equals(uri.getLastPathSegment())) {
            File dir = getContext().getExternalFilesDir(null);
            File[] files = dir != null ? dir.listFiles() : null;
            int n = 0;
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if ((Config.WALLPAPER_NAME.equals(name) || Config.WALLPAPER_SERIES.matcher(name).matches()) && f.delete()) n++;
                }
            }
            return n;
        }
        try {
            File file = target(uri);
            return file.exists() && file.delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
