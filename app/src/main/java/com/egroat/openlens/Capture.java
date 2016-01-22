package com.egroat.openlens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.opencv.core.Core.magnitude;
import static org.opencv.imgproc.Imgproc.getRotationMatrix2D;
import static org.opencv.imgproc.Imgproc.resize;
import static org.opencv.imgproc.Imgproc.warpAffine;

public class Capture extends AppCompatActivity implements View.OnClickListener,
        CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String     TAG                 = "OpenLens::Capture";
    private static final double     mScalingFactor      = 3.0;

    private CameraBridgeViewBase    mOpenCvCameraView   = null;
    private Mat                     mCaptureImage       = null;
    private MatOfPoint2f            mRect               = null;
    private processImage            mProcessImage       = new processImage();

    private static double angle(Point p1, Point p2, Point p0) {
        double dx1 = p1.x - p0.x;
        double dy1 = p1.y - p0.y;
        double dx2 = p2.x - p0.x;
        double dy2 = p2.y - p0.y;
        return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
    }
    private ArrayList<Point> sortCorners(ArrayList<Point> corners, Point center)
    {
        ArrayList<Point> top = new ArrayList<>(), bot= new ArrayList<>();

        for (int i = 0; i < corners.size(); i++)
        {
            if (corners.get(i).y < center.y)
                top.add(corners.get(i));
            else
                bot.add(corners.get(i));
        }

        Point tl = top.get(0).x > top.get(1).x ? top.get(1) : top.get(0);
        Point tr = top.get(0).x > top.get(1).x ? top.get(0) : top.get(1);
        Point bl = bot.get(0).x > bot.get(1).x ? bot.get(1) : bot.get(0);
        Point br = bot.get(0).x > bot.get(1).x ? bot.get(0) : bot.get(1);

        corners.clear();
        corners.add(tl);
        corners.add(tr);
        corners.add(br);
        corners.add(bl);
        return corners;
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onClick(View view) {
        Log.d(TAG, "TOUCH");
        if (mCaptureImage != null) {
            mProcessImage.cancel(true);
            mProcessImage = new processImage();
            mProcessImage.execute(new processImageParams(mCaptureImage,true));
            try {
                mRect = mProcessImage.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            if (mRect != null) {
                List<MatOfPoint> temp = new ArrayList<>();
                temp.add(new MatOfPoint(mRect.toArray()));
                Imgproc.drawContours(mCaptureImage, temp, 0, new Scalar(255, 0, 0));
                int i = 1;
                for(Point p: mRect.toArray()) {
                    Imgproc.circle(mCaptureImage, p, 8, new Scalar(255, 0, 0));
                    Imgproc.putText(mCaptureImage,"Point " + i++,p,Core.FONT_HERSHEY_PLAIN,2,new Scalar(255,255,255));
                }
                i=1;
                {
                    Imgproc.putText(mCaptureImage,"Point " + i++,new Point(mCaptureImage.size().width-200,100),Core.FONT_HERSHEY_PLAIN,2,new Scalar(255,255,255));
                    Imgproc.putText(mCaptureImage,"Point " + i++,new Point(200,200),Core.FONT_HERSHEY_PLAIN,2,new Scalar(255,255,255));
                    Imgproc.putText(mCaptureImage,"Point " + i++,new Point(100,mCaptureImage.size().height-200),Core.FONT_HERSHEY_PLAIN,2,new Scalar(255,255,255));
                    Imgproc.putText(mCaptureImage,"Point " + i++,new Point(mCaptureImage.size().width-200,mCaptureImage.size().height-200),Core.FONT_HERSHEY_PLAIN,2,new Scalar(255,255,255));
                }
            }
            //magnitude();
            Point[] corners = new Point[] {new Point(0.0,0.0),
                    new Point(mCaptureImage.size().width,0.0),
                    new Point(0.0,mCaptureImage.size().height),
                    new Point(mCaptureImage.size().width,mCaptureImage.size().height)};
            Point[] corners2 = mRect.toArray();


            //TODO: Figure out how to wrap image
            Mat dst = mCaptureImage.clone();
            Mat src_mat=new Mat(4,1,CvType.CV_32FC2);
            Mat dst_mat=new Mat(4,1,CvType.CV_32FC2);
            src_mat.put(0,0,corners2[0].x,corners2[0].y,
                    corners2[1].x,corners2[1].y,
                    corners2[2].x,corners2[2].y,
                    corners2[3].x,corners2[3].y);
            dst_mat.put(0, 0, corners[0].x, corners[0].y,
                    corners[1].x, corners[1].y,
                    corners[2].x, corners[2].y,
                    corners[3].x, corners[3].y);
            Mat perspectiveTransform = Imgproc.getPerspectiveTransform(src_mat,dst_mat);
            Imgproc.warpPerspective(mCaptureImage,dst,perspectiveTransform,mCaptureImage.size());
            //mCaptureImage = dst;

            Bitmap bmp = null;
            bmp = Bitmap.createBitmap(mCaptureImage.cols(), mCaptureImage.rows(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(mCaptureImage, bmp);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            Log.d(TAG, "BMP SIZE: " + stream.size());
            byte[] byteImage = stream.toByteArray();
            try {
                FileOutputStream fo = openFileOutput("image", Context.MODE_PRIVATE);
                fo.write(byteImage);
                fo.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            Intent display = new Intent(this, Display.class);
            Bundle b = new Bundle();
            display.putExtra("image", "image");

            Log.d(TAG, "Started new activity");

            mProcessImage.cancel(true);
            mRect = null;

            startActivity(display);

            //finish();
        }
    }

    private class processImageParams {
        public Mat mMat = null;
        public boolean mShowProgress = false;

        public processImageParams(Mat mat) {
            mMat = mat;
        }
        public processImageParams(Mat mat, boolean showProgress) {
            mMat = mat;
            mShowProgress = showProgress;
        }
    }

    private class processImage extends AsyncTask<processImageParams, Integer, MatOfPoint2f> {

        private int maxId = -1;
        private boolean showProgress = false;

        private MatOfPoint2f find(processImageParams src) {
            Mat blurred = src.mMat.clone();
            Imgproc.medianBlur(src.mMat, blurred, 9);

            Mat gray0 = new Mat(blurred.size(), CvType.CV_8U), gray = new Mat();

            List<MatOfPoint> contours = new ArrayList<>();

            List<Mat> blurredChannel = new ArrayList<>();
            blurredChannel.add(blurred);
            List<Mat> gray0Channel = new ArrayList<>();
            gray0Channel.add(gray0);

            MatOfPoint2f approxCurve;

            double maxArea = 0;

            for (int c = 0; c < 3; c++) {
                int ch[] = {c, 0};
                Core.mixChannels(blurredChannel, gray0Channel, new MatOfInt(ch));

                int thresholdLevel = 1;
                for (int t = 0; t < thresholdLevel; t++) {
                    if (t == 0) {
                        Imgproc.Canny(gray0, gray, 10, 20, 3, true); // true ?
                        Imgproc.dilate(gray, gray, new Mat(), new Point(-1, -1), 1); // 1 ?
                    } else {
                        Imgproc.adaptiveThreshold(gray0, gray, thresholdLevel,
                                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
                                (src.mMat.width() + src.mMat.height()) / 200, t);
                    }

                    Imgproc.findContours(gray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

                    for (MatOfPoint contour : contours) {
                        MatOfPoint2f temp = new MatOfPoint2f(contour.toArray());

                        double area = Imgproc.contourArea(contour);
                        approxCurve = new MatOfPoint2f();
                        Imgproc.approxPolyDP(temp, approxCurve, Imgproc.arcLength(temp, true) * 0.02, true);

                        if (approxCurve.total() == 4 && area >= maxArea) {
                            double maxCosine = 0;

                            List<Point> curves = approxCurve.toList();
                            for (int j = 2; j < 5; j++) {

                                double cosine = Math.abs(angle(curves.get(j % 4), curves.get(j - 2), curves.get(j - 1)));
                                maxCosine = Math.max(maxCosine, cosine);
                            }

                            if (maxCosine < 0.3) {
                                maxArea = area;
                                maxId = contours.indexOf(contour);
                                //TODO: Fix progress bar updating
                                if(src.mShowProgress)
                                    publishProgress(t / thresholdLevel);
                                //contours.set(maxId, getHull(contour));
                            }
                            if (isCancelled())
                                return null;
                        }
                    }
                }
            }

            if (maxId >= 0) {
                MatOfPoint2f temp = new MatOfPoint2f(contours.get(maxId).toArray());
                approxCurve = new MatOfPoint2f();
                Imgproc.approxPolyDP(temp, approxCurve, Imgproc.arcLength(temp, true) * 0.02, true);
                return approxCurve;
            }
            return null;
        }

        protected void onProgressUpdate(Integer... progress) {
            //TODO: Fix progress bar updating
            //Log.d(TAG,"Progress updated");
            setProgress(progress[0]);
        }

        protected MatOfPoint2f doInBackground(processImageParams... mats) {
            return find(mats[0]);
        }

        protected void onPreExecute() {
            setProgressBarVisibility(true);
        }

        protected void onPostExecute(MatOfPoint2f matOfPoint) {
            if (matOfPoint != null) {
                mRect = matOfPoint;
            }
            setProgressBarVisibility(false);
        }
    }

    public Capture() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_capture);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.view);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);

        mOpenCvCameraView.setOnClickListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat displayImage;
        {
            Mat mid = new Mat(), dst = new Mat(), rot_mat;
            Point src_center = new Point(inputFrame.rgba().cols() / 2.0, inputFrame.rgba().rows() / 2.0);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Log.d(TAG, "Rotation is: " + rotation);

            switch (rotation) {
                case 0:
                    rot_mat = getRotationMatrix2D(src_center, 270, 1.0);
                    warpAffine(inputFrame.rgba(), mid, rot_mat, inputFrame.rgba().size());
                    //FIXME: Is this extra stuff needed?
                    android.graphics.Point point = new android.graphics.Point();
                    getWindowManager().getDefaultDisplay().getSize(point);
                    resize(mid, dst, new Size(point.x, point.y));

                    mCaptureImage = mid;
                    break;
                case 3:
                    rot_mat = getRotationMatrix2D(src_center, 180, 1.0);
                    warpAffine(inputFrame.rgba(), mid, rot_mat, inputFrame.rgba().size());
                    mCaptureImage = mid;
                    break;
                case 1:
                default:
                    mCaptureImage = inputFrame.rgba().clone();
            }
        }

       if (!mProcessImage.getStatus().equals(AsyncTask.Status.RUNNING)) {
            Mat smaller = new Mat();
            resize(mCaptureImage, smaller,
                    new Size(mCaptureImage.width() / mScalingFactor,
                            mCaptureImage.height() / mScalingFactor));
            mProcessImage = new processImage();
            mProcessImage.execute(new processImageParams(smaller));
       }

       displayImage = mCaptureImage.clone();

       if (mRect != null) {
           MatOfPoint2f approxCurveCorrected = new MatOfPoint2f();
           List<Point> approxCurveCorrectedPoints = new ArrayList<>();
           for(Point approxCurveCorrectPoint: mRect.toArray()) {
               approxCurveCorrectPoint.x *= mScalingFactor;
               approxCurveCorrectPoint.y *= mScalingFactor;
               approxCurveCorrectedPoints.add(approxCurveCorrectPoint);
           }
           approxCurveCorrected.fromList(approxCurveCorrectedPoints);
           List<MatOfPoint> temp = new ArrayList<>();
           temp.add(new MatOfPoint(approxCurveCorrected.toArray()));
           Imgproc.drawContours(displayImage, temp, 0, new Scalar(255, 0, 0));
       }

       return displayImage;
    }
}
