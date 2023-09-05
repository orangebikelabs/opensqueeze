/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.orangebikelabs.orangesqueeze.compat.Compat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A few utility file methods.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class FileUtils {
    // yo no constructo
    private FileUtils() {
        // intentionally blank
    }

    /**
     * Moves the file from one path to another. This method can rename a file or
     * move it to a different directory, like the Unix {@code mv} command.
     *
     * @param from the source file
     * @param to   the destination file
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if {@code from.equals(to)}
     */
    public static void move(File from, File to) throws IOException {
        checkNotNull(from);
        checkNotNull(to);
        checkArgument(!from.equals(to),
                "Source %s and destination %s must be different", from, to);

        if (!from.renameTo(to)) {
            FileUtils.copy(from, to);
            if (!from.delete()) {
                if (!to.delete()) {
                    throw new IOException("Unable to delete " + to);
                }
                throw new IOException("Unable to delete " + from);
            }
        }
    }

    /**
     * copy routine that uses Okio for efficient buffering
     */
    static public void copy(File srcFile, File destFile) throws IOException {
        try(BufferedSource src = Okio.buffer(Okio.source(srcFile))) {
            try(BufferedSink sink = Okio.buffer(Okio.sink(destFile))) {
                sink.writeAll(src);
            }
        }
    }

    public static void deleteDirectory(File file) {
        deleteDirectory(file, new ArrayList<>());
    }

    @Nonnull
    public static String sanitizeFilename(String filename) {
        return filename.replaceAll("[:\\\\/*?|<>]", "_");
    }

    private static void deleteDirectory(File file, List<File> files) {
        if (file.isDirectory()) {
            File aFile = file.getAbsoluteFile();

            if (files.contains(aFile)) {
                // found recursion
                return;
            }
            files.add(aFile);
            File[] fileArray = aFile.listFiles();
            if (fileArray != null) {
                for (File f : fileArray) {
                    deleteDirectory(f, files);
                }
            }
            files.remove(aFile);
        }
        FileUtils.deleteChecked(file);
    }

    public static long getTotalSpace(File filesystem) {
        return Compat.getTotalSpace(filesystem);
    }

    public static void deleteChecked(File f) {
        boolean deleted = f.delete();
        if (!deleted) {
            OSLog.w("Failed to delete file " + f, new IOException("Failed to delete file " + f));
        }
    }

    public static void mkdirsChecked(File f) {
        boolean result = f.mkdirs();
        if (!result) {
            OSLog.w("Error creating directory " + f, new IOException("Error creating directory " + f));
        }
    }

    public static void mkdirsUnchecked(File f) {
        boolean result = f.mkdirs();
        if (!result) {
            OSLog.d("unchecked failure of mkdirs");
        }
    }
}
