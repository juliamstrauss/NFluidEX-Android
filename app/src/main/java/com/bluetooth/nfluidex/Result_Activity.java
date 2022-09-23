package com.bluetooth.nfluidex;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;

import nfluidex.R;

public class Result_Activity extends AppCompatActivity {
    public static final String EXTRA_Z_DIAG = "Z diag";
    public static final String EXTRA_Z_G = "Z g";
    public static final String EXTRA_Z_M = "Z m";

    public String ZdiagString;
    public String ZgString;
    public String ZmString;
    public double Zdiag;
    public double Zg;
    public double Zm;

    public final String TAG = "Result Activity";

    HorizontalBarChart chG;
    HorizontalBarChart chM;
    HorizontalBarChart chD;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        final Intent toResult = getIntent();
        ZdiagString = toResult.getStringExtra(EXTRA_Z_DIAG);
        ZgString = toResult.getStringExtra(EXTRA_Z_G);
        ZmString = toResult.getStringExtra(EXTRA_Z_M);

        chG = findViewById(R.id.chart1);
        chM = findViewById(R.id.chart2);
        chD = findViewById(R.id.chart3);

        if (ZdiagString != null){
            Zdiag = Double.parseDouble(ZdiagString);
        } else {
            Log.d(TAG, "EXTRA_Z_DIAG is null!");}
        if (ZgString != null){
            Zg = Double.parseDouble(ZgString);
        }else {
            Log.d(TAG, "EXTRA_Z_G is null!");}
        if (ZmString != null){
            Zm = Double.parseDouble(ZmString);
        }else {
            Log.d(TAG, "EXTRA_Z_M is null!");}
        addResultCurves();
        addChart(chG, 0);
        addChart(chM, 1);
        addChart(chD, 2);
    }

    public void addResultCurves(){
        ImageView resultCurves = findViewById(R.id.result_curves);
        TextView infection = findViewById(R.id.infection_status);

        //uninfected or recovery
        if (Zdiag < 250000){
            if (Zg < 250000){
                Log.d(TAG, "result: not infected");
                resultCurves.setImageResource(R.drawable.result_uninfected);
                infection.setText(R.string.infection_status_neg);
                infection.setTextColor(Color.rgb(1,82,6));
                infection.setBackgroundColor(Color.rgb(224,224,224));
            } else{
                Log.d(TAG, "result: recovering");
                resultCurves.setImageResource(R.drawable.result_recovering);
                infection.setText(R.string.infection_status_recovery);
                infection.setBackgroundColor(Color.rgb(224,224,224));
                infection.setTextColor(Color.rgb(0,24,145));
            }
        }
        else if (Zg < 250000) {
            Log.d(TAG, "result: early infection");
            resultCurves.setImageResource(R.drawable.result_earlyinfection);
            infection.setText(R.string.infection_status_early);
            infection.setBackgroundColor(Color.rgb(224,224,224));
            infection.setTextColor(Color.rgb(0,24,145));
        }
        else{
            Log.d(TAG, "result: peak infection");
            resultCurves.setImageResource(R.drawable.result_peakinfection);
            infection.setText(R.string.infection_status_peak);
            infection.setBackgroundColor(Color.rgb(224,224,224));
            infection.setTextColor(Color.rgb(138,0,0));
        }
    }
    public void addChart(HorizontalBarChart ch, int type){
        double x = 0.1;
        double y;
        if (type == 0){
            y = Zg;
        }
        else if (type == 1){
            y = Zm;
        }
        else {
            y = Zdiag;
        }
        BarEntry entry = new BarEntry((float) x,(float) y);
        ArrayList<BarEntry> entries = new ArrayList<>(3);
        BarEntry ref1 = new BarEntry((float) 0.1, 1000000);
        entries.add(ref1);
        BarEntry ref2;
        BarEntry ref3;
        BarDataSet dataset;
        if (y < 250000) {
            ref2 = new BarEntry((float) 0.1, 250000);
            ref3 = new BarEntry((float) 0.1, (float) y-5000);
            entries.add(ref2);
            entries.add(entry);
            entries.add(ref3);
            dataset = new BarDataSet(entries,"");
            dataset.setColors(Color.rgb(253, 120, 195), Color.rgb(58, 168, 44), Color.BLACK, Color.rgb(58, 168, 44));

        } else{
            entries.add(entry);
            ref2 = new BarEntry((float) 0.1, (float) y-5000);
            ref3 = new BarEntry((float) 0.1, 250000);
            entries.add(ref2);
            entries.add(ref3);
            dataset = new BarDataSet(entries,"");
            dataset.setColors(Color.rgb(253, 120, 195), Color.BLACK, Color.rgb(253, 120, 195), Color.rgb(58, 168, 44));
        }

        initBarDataSet(dataset);
        BarData data = new BarData(dataset);
        YAxis axisYleft = ch.getAxisLeft();
        YAxis axisYright = ch.getAxisRight();
        XAxis axisX = ch.getXAxis();
        axisX.setEnabled(false);
        axisYleft.setAxisMinimum(0f);
        axisYleft.setAxisMaximum(1000000f);
        axisYright.setAxisMinimum(0f);
        axisYright.setAxisMaximum(1000000f);
        axisYright.setEnabled(false);
        axisYleft.setEnabled(true);
        axisYleft.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int A = (int) value;
                return A/1000+"kΩ";
            }
        });
        ch.getLegend().setEnabled(false);
        Description desc = ch.getDescription();
        if (type == 0){
            desc.setText("IgG");
        }
        else if (type ==1){
            desc.setText("IgM");
        }
        else{
            desc.setText("");
        }
        desc.setTextSize(20f);
        desc.setPosition(150, 150);
        desc.setEnabled(true);
        ch.setData(data);
        ch.invalidate();
    }
    private void initBarDataSet(BarDataSet dataset){
        dataset.setValueTextSize(0f);
    }
}