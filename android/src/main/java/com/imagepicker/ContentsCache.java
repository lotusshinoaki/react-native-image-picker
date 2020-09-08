package com.imagepicker;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.UUID;

/**
 * The "Scoped storage" applied in Android Q and above completely disables file scheme access
 * outside the app's private area.
 * This class is useful for copying content scheme files to your app's private area and enabling
 * file scheme access.
 * */
public class ContentsCache {
    private static final String TAG = "ContentsCache";
    private final File mCacheDir;
    private final LinkedList<File> mCachedList = new LinkedList<>();
    private final ContentResolver mContentResolver;
    private final int mMaxNumOfCaches;

    public ContentsCache(@NonNull Context context,
                         @NonNull String cacheDirName,
                         int maxNumOfCaches) {
        mCacheDir = new File(context.getCacheDir(), cacheDirName);
        mContentResolver = context.getContentResolver();
        mMaxNumOfCaches = Math.max(maxNumOfCaches, 1);

        for (File file : getCachedFiles()) {
            addToCachedFileList(file);
        }
    }

    @NonNull
    public File add(@NonNull Uri uri) throws IOException {
        File cachedFile = addToCachedFile(uri);
        addToCachedFileList(cachedFile);
        return cachedFile;
    }

    private void addToCachedFileList(@NonNull File cacheFile) {
        if (mCachedList.size() >= mMaxNumOfCaches) {
            //noinspection ResultOfMethodCallIgnored
            mCachedList.removeLast().delete();
            Log.d(TAG, "Cached file deleted: " + cacheFile);
        }
        mCachedList.addFirst(cacheFile);
    }

    @NonNull
    private File addToCachedFile(@NonNull Uri uri) throws IOException {
        if (!"content".equals(uri.getScheme())) {
            throw new IllegalArgumentException("URI scheme must be \"content\"");
        }

        if (!mCacheDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mCacheDir.mkdirs();
        }

        InputStream input = null;
        OutputStream output = null;
        try {
            input = mContentResolver.openInputStream(uri);
            if (input == null) {
                throw new FileNotFoundException(uri.toString());
            }
            input = new BufferedInputStream(input);
            File cacheFile = new File(mCacheDir, UUID.randomUUID().toString());
            output = new BufferedOutputStream(new FileOutputStream(cacheFile));
            copyTo(input, output);
            return cacheFile;
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    @NonNull
    private File[] getCachedFiles() {
        File[] files = mCacheDir.listFiles();
        if (files == null) {
            return new File[0];
        }

        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                long lm1 = o1.lastModified();
                long lm2 = o2.lastModified();
                //noinspection UseCompareMethod
                return (lm1 < lm2) ? -1 : ((lm1 == lm2) ? 0 : 1);
            }
        });
        return files;
    }

    private static void closeQuietly(@Nullable InputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            Log.w(TAG, e);
        }
    }

    private static void closeQuietly(@Nullable OutputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            Log.w(TAG, e);
        }
    }

    private static void copyTo(@NonNull InputStream input, @NonNull OutputStream output)
            throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = input.read(buf)) != -1) {
            output.write(buf, 0, len);
        }
    }
}
