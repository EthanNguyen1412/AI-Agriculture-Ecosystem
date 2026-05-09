package com.nckh.drplant.AiUtils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;

public final class OnnxImagePreprocessor {

    private static final int MAX_DECODE_DIMENSION = 1600;
    private static final int LETTERBOX_COLOR = Color.rgb(114, 114, 114);

    private OnnxImagePreprocessor() {
        // Utility class
    }

    // Gói dữ liệu trung gian sau khi ảnh được xoay đúng chiều, resize kiểu letterbox và đổi sang tensor float.
    public static final class PreprocessedImage {
        public final float[] tensorData;
        public final int sourceWidth;
        public final int sourceHeight;
        public final float scale;
        public final int padLeft;
        public final int padTop;

        PreprocessedImage(@NonNull float[] tensorData,
                          int sourceWidth,
                          int sourceHeight,
                          float scale,
                          int padLeft,
                          int padTop) {
            this.tensorData = tensorData;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.scale = scale;
            this.padLeft = padLeft;
            this.padTop = padTop;
        }
    }

    // Đọc ảnh từ file, xoay đúng chiều theo EXIF rồi tiền xử lý theo chuẩn YOLO đầu vào 640x640.
    @NonNull
    public static PreprocessedImage preprocessImageFile(@NonNull File imageFile,
                                                        int inputWidth,
                                                        int inputHeight) throws IOException {
        Bitmap sampledBitmap = decodeSampledBitmap(imageFile.getAbsolutePath(), MAX_DECODE_DIMENSION, MAX_DECODE_DIMENSION);
        if (sampledBitmap == null) {
            throw new IOException("Không thể giải mã ảnh đầu vào.");
        }

        Bitmap orientedBitmap = applyExifOrientation(imageFile.getAbsolutePath(), sampledBitmap);
        try {
            return letterboxAndNormalize(orientedBitmap, inputWidth, inputHeight);
        } finally {
            if (orientedBitmap != sampledBitmap) {
                orientedBitmap.recycle();
            }
            sampledBitmap.recycle();
        }
    }

    // Giải mã ảnh với hệ số thu nhỏ hợp lý để hạn chế tăng bộ nhớ khi đọc ảnh gốc độ phân giải cao.
    @Nullable
    private static Bitmap decodeSampledBitmap(@NonNull String imagePath, int requiredWidth, int requiredHeight) {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, boundsOptions);

        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inSampleSize = calculateInSampleSize(boundsOptions, requiredWidth, requiredHeight);
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(imagePath, bitmapOptions);
    }

    // Tính hệ số thu nhỏ gần đúng để bitmap sau giải mã vẫn đủ rõ nhưng không quá lớn.
    private static int calculateInSampleSize(@NonNull BitmapFactory.Options options, int requiredWidth, int requiredHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > requiredHeight || width > requiredWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= requiredHeight && (halfWidth / inSampleSize) >= requiredWidth) {
                inSampleSize *= 2;
            }
        }

        return Math.max(inSampleSize, 1);
    }

    // Xoay hoặc lật ảnh theo metadata EXIF để model nhận được ảnh đúng hướng thật của lá.
    @NonNull
    private static Bitmap applyExifOrientation(@NonNull String imagePath, @NonNull Bitmap bitmap) {
        try {
            ExifInterface exifInterface = new ExifInterface(imagePath);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180f);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix.preScale(-1f, 1f);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix.preScale(1f, -1f);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix.preScale(-1f, 1f);
                    matrix.postRotate(270f);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix.preScale(-1f, 1f);
                    matrix.postRotate(90f);
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                case ExifInterface.ORIENTATION_UNDEFINED:
                default:
                    return bitmap;
            }

            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );
            return rotatedBitmap;
        } catch (Exception exception) {
            return bitmap;
        }
    }

    // Resize ảnh theo kiểu letterbox như YOLO, thêm padding nền xám rồi chuyển sang tensor RGB NCHW chuẩn hóa 0..1.
    @NonNull
    private static PreprocessedImage letterboxAndNormalize(@NonNull Bitmap sourceBitmap,
                                                           int inputWidth,
                                                           int inputHeight) {
        int sourceWidth = sourceBitmap.getWidth();
        int sourceHeight = sourceBitmap.getHeight();
        float scale = Math.min((float) inputWidth / sourceWidth, (float) inputHeight / sourceHeight);
        int resizedWidth = Math.max(1, Math.round(sourceWidth * scale));
        int resizedHeight = Math.max(1, Math.round(sourceHeight * scale));
        int padLeft = (inputWidth - resizedWidth) / 2;
        int padTop = (inputHeight - resizedHeight) / 2;

        Bitmap inputBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(inputBitmap);
        canvas.drawColor(LETTERBOX_COLOR);

        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        Rect destination = new Rect(padLeft, padTop, padLeft + resizedWidth, padTop + resizedHeight);
        canvas.drawBitmap(sourceBitmap, null, destination, paint);

        float[] tensorData = toNormalizedFloatTensor(inputBitmap);
        inputBitmap.recycle();
        return new PreprocessedImage(tensorData, sourceWidth, sourceHeight, scale, padLeft, padTop);
    }

    // Chuyển bitmap đầu vào thành mảng float dạng NCHW để có thể tạo OnnxTensor cho model.
    @NonNull
    private static float[] toNormalizedFloatTensor(@NonNull Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int channelSize = width * height;
        int[] pixels = new int[channelSize];
        float[] tensorData = new float[channelSize * 3];

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int index = 0; index < channelSize; index++) {
            int pixel = pixels[index];
            tensorData[index] = ((pixel >> 16) & 0xFF) / 255f;
            tensorData[channelSize + index] = ((pixel >> 8) & 0xFF) / 255f;
            tensorData[(channelSize * 2) + index] = (pixel & 0xFF) / 255f;
        }

        return tensorData;
    }
}
