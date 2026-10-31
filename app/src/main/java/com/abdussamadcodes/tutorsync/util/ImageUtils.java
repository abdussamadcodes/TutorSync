package com.abdussamadcodes.tutorsync.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class ImageUtils {

    private static final String PROFILE_IMAGE_DIR = "profile_images";
    private static final int TARGET_SQUARE_DIMENSION = 500;
    private static final int JPEG_QUALITY = 80;

    private ImageUtils() {
        // Utility class
    }

    /**
     * Decodes an image from a content Uri, fixes camera/selfie EXIF orientation,
     * center-crops and resizes to a 500x500 square, compresses to 80% JPEG in
     * internal storage, and deletes the replaced profile photo if provided.
     */
    @Nullable
    public static String compressAndSaveImage(
            @NonNull Context context,
            @NonNull Uri imageUri,
            @NonNull String filePrefix,
            @Nullable String oldImagePath) {

        try {
            // 1. Decode image dimensions only to avoid OutOfMemoryError on large camera photos
            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            try (InputStream boundsStream = context.getContentResolver().openInputStream(imageUri)) {
                if (boundsStream == null) return null;
                BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
            }

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null;
            }

            // 2. Decode a subsampled mutable bitmap near 500x500
            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = calculateInSampleSize(
                    boundsOptions, TARGET_SQUARE_DIMENSION, TARGET_SQUARE_DIMENSION);
            decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bitmap;
            try (InputStream decodeStream = context.getContentResolver().openInputStream(imageUri)) {
                if (decodeStream == null) return null;
                bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions);
            }
            if (bitmap == null) return null;

            // 3. Read EXIF orientation from stream and rotate/flip if needed
            int orientation = ExifInterface.ORIENTATION_NORMAL;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try (InputStream exifStream = context.getContentResolver().openInputStream(imageUri)) {
                    if (exifStream != null) {
                        ExifInterface exif = new ExifInterface(exifStream);
                        orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    }
                } catch (Exception ignored) {
                }
            }
            bitmap = rotateBitmapIfRequired(bitmap, orientation);

            // 4. Center-crop to a 1:1 square and resize to 500x500
            bitmap = centerCropAndResizeSquare(bitmap, TARGET_SQUARE_DIMENSION);

            // 5. Compress to 80% JPEG in internal storage (files/profile_images/)
            File dir = new File(context.getFilesDir(), PROFILE_IMAGE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                bitmap.recycle();
                return null;
            }

            String safePrefix = filePrefix.replaceAll("[^a-zA-Z0-9_-]", "_");
            if (safePrefix.isEmpty()) {
                safePrefix = "profile";
            }
            File outFile = new File(dir, safePrefix + "_" + System.currentTimeMillis() + ".jpg");

            try (FileOutputStream out = new FileOutputStream(outFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
                out.flush();
            } finally {
                bitmap.recycle();
            }

            // 6. Delete replaced profile photo if it differs from the newly written file
            if (oldImagePath != null && !oldImagePath.equals(outFile.getAbsolutePath())) {
                deleteProfileImage(context, oldImagePath);
            }

            return outFile.getAbsolutePath();
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static String compressAndSaveImage(
            @NonNull Context context,
            @NonNull Uri imageUri,
            @NonNull String filePrefix) {
        return compressAndSaveImage(context, imageUri, filePrefix, null);
    }

    public static boolean loadProfileImageIntoView(
            @Nullable String imagePath,
            @NonNull ImageView imageView) {
        Bitmap bitmap = loadBitmapFromPath(imagePath);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return true;
        }
        return false;
    }

    @Nullable
    public static Bitmap loadBitmapFromPath(@Nullable String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }
        File file = new File(imagePath);
        if (!file.exists() || !file.isFile() || file.length() <= 0) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    /**
     * Safely deletes a previous profile photo stored inside the app's internal files directory
     * (supports both files/profile_images/* and legacy files/profile_*.jpg).
     */
    public static void deleteProfileImage(@NonNull Context context, @Nullable String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;
        try {
            File file = new File(imagePath);
            if (!file.exists() || !file.isFile()) return;

            String canonicalFile = file.getCanonicalPath();
            String canonicalFilesDir = context.getFilesDir().getCanonicalPath();

            if (canonicalFile.startsWith(canonicalFilesDir)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private static int calculateInSampleSize(
            @NonNull BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    @NonNull
    private static Bitmap rotateBitmapIfRequired(@NonNull Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.preScale(-1.0f, 1.0f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.preScale(1.0f, -1.0f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.postRotate(90);
                matrix.preScale(-1.0f, 1.0f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.postRotate(270);
                matrix.preScale(-1.0f, 1.0f);
                break;
            default:
                return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    @NonNull
    private static Bitmap centerCropAndResizeSquare(@NonNull Bitmap source, int targetDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        int squareSize = Math.min(width, height);
        int xOffset = (width - squareSize) / 2;
        int yOffset = (height - squareSize) / 2;

        Bitmap square = Bitmap.createBitmap(source, xOffset, yOffset, squareSize, squareSize);
        if (square != source) {
            source.recycle();
        }

        if (squareSize != targetDimension) {
            Bitmap scaled = Bitmap.createScaledBitmap(square, targetDimension, targetDimension, true);
            if (scaled != square) {
                square.recycle();
            }
            return scaled;
        }
        return square;
    }
}