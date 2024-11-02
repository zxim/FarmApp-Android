package com.example.farm;

import static com.example.farm.Fragment.CameraFragment.rotateImage;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.farm.Connection.AISocket;
import com.example.farm.Connection.SearchTask;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.gson.Gson;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FruitFreshUploadActivity extends AppCompatActivity {
    private ImageView fruit_image;
    private TextView fruit_name, fruit_fresh, fruit_maturity, maturity_tv, maturity_tv2;
    private HorizontalBarChart fresh_graph, matuity_graph;
    private ImageButton back_btn, info_btn;
    private Bitmap photo;
    private ShimmerFrameLayout shimmerFrameLayout1, shimmerFrameLayout2;
    private ProgressDialog loadingDialog;
//    private Button ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fruitfresh_uploadlayout);

        Intent intent = getIntent();

        fruit_image = findViewById(R.id.fruit_img);
        fruit_name = findViewById(R.id.fruit_name);
        fruit_fresh = findViewById(R.id.fruit_fresh);
        fresh_graph = findViewById(R.id.fresh_graph);
        back_btn = findViewById(R.id.back_btn);
        info_btn = findViewById(R.id.info_btn);
        fruit_maturity = findViewById(R.id.fruit_maturity);
        shimmerFrameLayout1 = findViewById(R.id.simmer_layout1);
        maturity_tv = findViewById(R.id.maturity_tv);
        matuity_graph = findViewById(R.id.matuiry_graph);
        shimmerFrameLayout2 = findViewById(R.id.shimmer_layout2);
        maturity_tv2 = findViewById(R.id.maturity_tv2);

        shimmerFrameLayout1.startShimmer();
        shimmerFrameLayout2.startShimmer();

        fruit_maturity.setText("00");

        String path = intent.getStringExtra("imageURI");
        Log.i("Image URI : ", path);
        Glide.with(getApplicationContext())
                .asBitmap()
                .load(path)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        fruit_image.setImageBitmap(resource);
                        startActivity(resource);
                    }
                });
    }

    private void startActivity(Bitmap photo){
        if(photo != null) {
            SocketTask task = new SocketTask();
            try {
                task.execute(photo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            Log.i("안됨안됨", "Null임");
        }
    }

    private void updateUI(List<String> result, Bitmap photo){
        //---------------------------------------------------------------------------- case 1 : 객체가 1개 인식되는 경우
        if (result.size() == 1) {
            setMaturityChart("100");
            // 과일이름(한글), 신선도정도, 신선도 수치
            String results = freshGuess(photo, this, result.get(0));
            String[] nameAndStatus = results.split(" ");
            String f_name = nameAndStatus[0];
            float fresh_num = Float.parseFloat(nameAndStatus[2]);
            fruit_name.setText(nameAndStatus[0]);

            switch (nameAndStatus[1]) {
                case "normal":
                    fruit_fresh.setText("상태 : 보통");
                case "rotten":
                    fruit_fresh.setText("상태 : 썩음");
                case "fresh":
                    fruit_fresh.setText("상태 : 신선함");
            }

            back_btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });

            // 신선도 그래프 설정
            fresh_graph.setDrawBarShadow(true);
            fresh_graph.setScaleEnabled(false);
            fresh_graph.setClickable(false);

            ArrayList<Float> li = new ArrayList<>();
            Log.i("추론 데이터(신선도) : ", fresh_num + "");
            li.add(fresh_num);
            BarDataSet dataSet = getBarDataSet(li);
            dataSet.setColor(Color.rgb(147, 247, 250));
            BarData data = new BarData(dataSet);
            fresh_graph.setData(data);
            fresh_graph.setDrawValueAboveBar(false);

            XAxis xAxis = fresh_graph.getXAxis();
            xAxis.setDrawGridLines(false);
            xAxis.setDrawAxisLine(false);
            xAxis.setEnabled(false);

            YAxis yLeft = fresh_graph.getAxisLeft();
            yLeft.setAxisMinimum(0f);
            yLeft.setAxisMaximum(100f);
            yLeft.setEnabled(false);

            YAxis yRight = fresh_graph.getAxisRight();
            yRight.setDrawGridLines(false);
            yRight.setDrawAxisLine(true);
            yRight.setEnabled(false);
            yRight.setDrawLabels(false);

            fresh_graph.setTouchEnabled(false);
            fresh_graph.animateY(1000);

            info_btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getApplicationContext(), FruitInformationActivity.class);
                    SearchTask task = new SearchTask();
                    Fruit fruit;
                    try {
                        fruit = task.execute(f_name).get();
                    } catch (ExecutionException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    intent.putExtra("info", fruit);
                    startActivity(intent);
                }
            });
            //---------------------------------------------------------------------------- case 2 : 객체가 1개 이상 인식되는 경우
        } else if (result.size() > 1) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(FruitFreshUploadActivity.this);
            dialog.setIcon(R.drawable.logo).setMessage("하나의 과일만 촬영해주세요.").setPositiveButton("다시 촬영하기", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            }).show();
            //---------------------------------------------------------------------------- case 3 : 객체가 인식되지 않는 경우
        } else {
            AlertDialog.Builder dialog = new AlertDialog.Builder(FruitFreshUploadActivity.this);
            dialog.setIcon(R.drawable.logo).setMessage("과일이 인식되지 않았습니다.").setPositiveButton("다시 촬영하기", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            }).show();
        }
    }

    private void startLoading(){
        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage("과일 신선도 판별중...");
        loadingDialog.setCancelable(true);
        loadingDialog.setProgressStyle(android.R.style.Widget_ProgressBar_Horizontal);
        loadingDialog.show();
    }

    private String freshGuess(Bitmap bitmap, Context context, String fruit_name){
        // TFlite객체 생성
        TFlite lite = new TFlite(context);

        // 4byte(float)크기의 3채널 224, 224배열 자료형
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4);  // 224 X 224크기 3채널 이미지 4바이트
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[224 * 224];
        bitmap.getPixels(pixels, 0, 224, 0, 0, 224, 224);

        int pixelIndex = 0;
        for (int row = 0; row < 224; row++) {
            for (int col = 0; col < 224; col++) {
                final int pixel = pixels[pixelIndex++];
                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float g = ((pixel >> 8) & 0xFF) / 255.0f;
                float b = (pixel & 0xFF) / 255.0f;

                inputBuffer.putFloat(r);
                inputBuffer.putFloat(g);
                inputBuffer.putFloat(b);
            }
        }

        // Interpreter를 통해 tflite파일 모델을 불러옴
        Interpreter tflite = lite.getTfliteInterpreter("model_unquant.tflite");
        Log.i("TensorFlow count : ", tflite.getOutputTensorCount() + "");

        float[][] outputs2 = new float[1][6];

        // tflite를 실행 인자(인자1 : 전달할 데이터, 인자2 : 출력된 데이터를 받을 데이터)
        tflite.run(inputBuffer, outputs2);
        String temp = findFruitName(outputs2, fruit_name);

        Log.i("fruit_name : ", temp.split(" ")[1]);
        Log.i("AI Result1 : ", String.format("%.2f", outputs2[0][0]) + "");
        Log.i("AI Result2 : ", String.format("%.2f", outputs2[0][1]) + "");
        Log.i("AI Result3 : ", String.format("%.2f", outputs2[0][2]) + "");
        Log.i("AI Result4 : ", String.format("%.2f", outputs2[0][3]) + "");
        Log.i("AI Result5 : ", String.format("%.2f", outputs2[0][4]) + "");
        Log.i("AI Result6 : ", String.format("%.2f", outputs2[0][5]) + "");

        // 과일 이름(한글), 신선도 판별결과
        return temp;
    }

    private BarDataSet getBarDataSet(ArrayList<Float> data){
        ArrayList<BarEntry> list = new ArrayList<>();

        list.add(new BarEntry(0f, data.get(0)));
        BarDataSet dataSet = new BarDataSet(list, "");
        dataSet.setColors(ColorTemplate.COLORFUL_COLORS);
        dataSet.setLabel("신선도");
        dataSet.setValueTextSize(10f);
        dataSet.setDrawIcons(false);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return (int)value + "%";
            }
        });
        return dataSet;
    }

    // 식별된 과일의 Label을 찾는 함수
    private String findFruitName(float[][] result, String en_name){
        int index = 5;
        float max_value = 0;
        String status = "판별 불가";
        String kor_name = "과일";
        String fresh_data = "0";

        try{
            InputStream labelInput = getResources().getAssets().open("labels.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(labelInput));
            String line;
            int cnt = 0;
            while(cnt <= index){
                line = br.readLine();
                Log.i("label.txt파일 읽어오기(한줄) : ", line + "");
                String[] words = line.split(" ");
                if(words[1].equals(en_name) && max_value < result[0][cnt]) {
                    status = words[3];
                    kor_name = words[2];
                    max_value = result[0][cnt];
                    fresh_data = result[0][cnt] * 100 + "";
                    Log.i("Fresh_data 출력 : ", fresh_data);
                }
                cnt++;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        Log.i("전달 Fresh_data : ", fresh_data + "");
        return kor_name + " " + status + " " + fresh_data;
    }

    private class SocketTask extends AsyncTask<Bitmap, Void, List<String>>{
        Bitmap bitmap = null;
        @Override
        protected void onPreExecute(){
            startLoading();
        }
        @Override
        protected List<String> doInBackground(Bitmap... bitmaps) {
            bitmap = bitmaps[0];
            AISocket socket = new AISocket();
            List<String> result = socket.communication(bitmaps[0]);
            Log.i("Activity Result : ", new Gson().toJson(result));
            return result;
        }

        // 스레드의 반환값을 이용하여 수행
        @Override
        protected void onPostExecute(List<String> s) {
            loadingDialog.dismiss();
            updateUI(s, bitmap);
        }
    }

    public void setMaturityChart(String num){
        // 성숙도 textView 제어
        shimmerFrameLayout1.stopShimmer();
        maturity_tv.setVisibility(View.INVISIBLE);
        fruit_maturity.setVisibility(View.VISIBLE);
        shimmerFrameLayout1.setVisibility(View.INVISIBLE);
        fruit_maturity.setText("성숙도 : " + num + "%");

        // 성숙도 Barchart제어
        shimmerFrameLayout2.stopShimmer();
        maturity_tv2.setVisibility(View.INVISIBLE);
        shimmerFrameLayout2.setVisibility(View.INVISIBLE);
        matuity_graph.setVisibility(View.VISIBLE);

        matuity_graph.setDrawBarShadow(true);
        matuity_graph.setScaleEnabled(false);
        matuity_graph.setClickable(false);
        matuity_graph.setTouchEnabled(false);

        ArrayList<Float> li = new ArrayList<>();
        li.add(Float.parseFloat(num));
        BarDataSet dataSet = getBarDataSet(li);
        dataSet.setColors(Color.rgb(248, 236, 135));
        BarData data = new BarData(dataSet);
        matuity_graph.setData(data);
        matuity_graph.setDrawValueAboveBar(false);

        XAxis xAxis = matuity_graph.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setEnabled(false);

        YAxis yLeft = matuity_graph.getAxisLeft();
        yLeft.setAxisMinimum(0f);
        yLeft.setAxisMaximum(100f);
        yLeft.setEnabled(false);

        YAxis yRight = matuity_graph.getAxisRight();
        yRight.setDrawGridLines(false);
        yRight.setDrawAxisLine(true);
        yRight.setEnabled(false);

        matuity_graph.animateY(1000);
    }

}
