package com.diplomskitrud.identifikuvanjenabolesti_v1;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

public class ImageClassifier {

    private static final String TAG = "classifier";

    //name of the model into a constant and labels loaded
    private static final String MODEL_PATH = "plant_disease_model_v1.lite";
    private static final String LABEL_PATH = "retrained_labels.txt";

    private static final int RESULTS_TO_SHOW = 3;

    //DIMENSIONS OF INPUTS
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;

    public static final int DIM_IMG_SIZE_X = 224;
    public static final int DIM_IMG_SIZE_Y = 224;

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;

    //PRELOCATED BUFFERS FOR STORING IMAGE DATA IN
    private int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

    //INSTANCES(OBJ)
    private Interpreter tflite; // OBJ OF INTERPRETER TO RUN THE MODEL INFERENCE VIA TENSOR FLOW
    private List<String> labelList;//OUTPUT OF THE LABELS CORRESPONDING TO THE MODEL
    private ByteBuffer imgData = null; // THIS WILL HOLD THE IMAGEDATA THAT WILL BE FEED INTO TENSORFLOW LITE AS INPUT

    //array that hold the inference result to be feed into tensorflow lite as output
    private float[][] labelProbArray = null;

    private float[][] filterLabelProbArray = null;
    private static final int FILTER_STAGES = 3;
    private static final float FILTER_FACTOR = 0.4f;

    private PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float> o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });

    /** Initializes an {@code ImageClassifier}. */
    ImageClassifier(Activity activity) throws IOException {
        tflite = new Interpreter(loadModelFile(activity));
        labelList = loadLabelList(activity);
        imgData =
                ByteBuffer.allocateDirect(
                        4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());
        labelProbArray = new float[1][labelList.size()];
        filterLabelProbArray = new float[FILTER_STAGES][labelList.size()];
        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");
    }

    /** Classifies a frame from the preview stream. */
    String classifyFrame(Bitmap bitmap) {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            return "Uninitialized Classifier.";
        }
        convertBitmapToByteBuffer(bitmap);
        // Here's where the magic happens!!!
        long startTime = SystemClock.uptimeMillis();
        tflite.run(imgData, labelProbArray);
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Timecost to run model inference: " + Long.toString(endTime - startTime));

        // smooth the results
        applyFilter();

        // print the results
        String textToShow = printTopKLabels();
        textToShow = Long.toString(endTime - startTime) + "ms" + textToShow;
        return textToShow;
    }

    void applyFilter(){
        int num_labels =  labelList.size();

        // Low pass filter `labelProbArray` into the first stage of the filter.
        for(int j=0; j<num_labels; ++j){
            filterLabelProbArray[0][j] += FILTER_FACTOR*(labelProbArray[0][j] -
                    filterLabelProbArray[0][j]);
        }
        // Low pass filter each stage into the next.
        for (int i=1; i<FILTER_STAGES; ++i){
            for(int j=0; j<num_labels; ++j){
                filterLabelProbArray[i][j] += FILTER_FACTOR*(
                        filterLabelProbArray[i-1][j] -
                                filterLabelProbArray[i][j]);

            }
        }

        // Copy the last stage filter output back to `labelProbArray`.
        for(int j=0; j<num_labels; ++j){
            labelProbArray[0][j] = filterLabelProbArray[FILTER_STAGES-1][j];
        }
    }

    /** Closes tflite to release resources. */
    public void close() {
        tflite.close();
        tflite = null;
    }

    /** Reads label list from Assets. */
    private List<String> loadLabelList(Activity activity) throws IOException {
        List<String> labelList = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(activity.getAssets().open(LABEL_PATH)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /** Writes Image data into a {@code ByteBuffer}. */
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // Convert the image to floating point.
        int pixel = 0;
        long startTime = SystemClock.uptimeMillis();
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                imgData.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                imgData.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
            }
        }
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Timecost to put values into ByteBuffer: " + Long.toString(endTime - startTime));
    }

    /** Prints top-K labels, to be shown in UI as the results. */
    private String printTopKLabels() {
        for (int i = 0; i < labelList.size(); ++i) {
            sortedLabels.add(
                    new AbstractMap.SimpleEntry<>(labelList.get(i), labelProbArray[0][i]));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }
        String textToShow = "";
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            textToShow = String.format("\n%s: %4.2f" , label.getKey(),label.getValue()) + textToShow;
        }
        return textToShow;
    }
}
