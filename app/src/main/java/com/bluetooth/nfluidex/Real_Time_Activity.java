package com.bluetooth.nfluidex;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import java.util.Calendar;
import java.util.GregorianCalendar;

import com.github.mikephil.charting.utils.ColorTemplate;

import nfluidex.R;

public class Real_Time_Activity extends Activity {
    // for Debugging...
    private static final String TAG = "Real_Time_Activity";
    private static final boolean D = true;


    // Data keys for data returned from BLEService:
    public static final String FREQUENCY_KEY = "frequency";
    public static final String IMPEDANCE_MAGNITUDE_KEY = "zmag";
    public static final String IMPEDANCE_PHASE_KEY = "zphase";
    public static final String POTENTIAL_KEY = "voltage";
    public static final String CURRENT_KEY = "current";

    private String FileName = "";
    private static final String Title = "T";
    private static final int PERMISSIONS_REQUEST_CODE = 1;

    // Handler Request Codes for mBTService
    public static final int CV_DATA = 30;
    public static final int CV_2_DATA = 300;
    public static final int EIS_DATA = 33;
    public static final int EIS_2_DATA = 330;
    public static final int DPV_DATA = 36;
    public static final int DPV_2_DATA = 360;
    public static final int CYCLE_COMPLETE = 39;

    public static int Method;
    public static int CVtrial = 0;

    private static final String EXIT_STRING = "x";
    private static final char tab = '\t';

    public static boolean BT_Break = false;	// variable to see if Bluetooth connection has already
    // been lost at least once during reaction; if so next connection
    // starts remote reset..

    public static boolean secondMeasurementIncoming = false;

    HorizontalBarChart chartM;
    HorizontalBarChart chartG;
    ScatterChart scatterChart;
    ScatterDataSet scatterDataSet;
    ScatterData scatterData;

    private final Timer loggingTimer = new Timer();
    private ArrayList<String> Frequency = new ArrayList<>();
    private ArrayList<String> Frequency2 = new ArrayList<>();
    private ArrayList<String> Z = new ArrayList<>();
    private ArrayList<String> Z2 = new ArrayList<>();
    private ArrayList<String> Zi = new ArrayList<>();
    private ArrayList<String> Zi2 = new ArrayList<>();
    private ArrayList<String> CV_potential = new ArrayList<>();
    private ArrayList<String> CV_current = new ArrayList<>();
    private ArrayList<String> CV_potential_2 = new ArrayList<>();
    private ArrayList<String> CV_current_2 = new ArrayList<>();
    private ArrayList<String> DPV_potential = new ArrayList<>();
    private ArrayList<String> DPV_current = new ArrayList<>();
    private ArrayList<String> DPV_potential_2 = new ArrayList<>();
    private ArrayList<String> DPV_current_2 = new ArrayList<>();


    String startf = "0.1";
    String endf = "100000";
    String signalamp = "0.01";
    String biasvoltage = "0";
    String scanrate = "0.02"; //units V/s

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";
    public static BLEService BLEAdapter;
    private String device_address;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()   // or .detectAll() for all detectable problems
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
        setContentView(R.layout.realtime);

        //Read intent data
        final Intent intent = getIntent();
        String device_name = intent.getStringExtra(EXTRA_NAME);
        device_address = intent.getStringExtra(EXTRA_ID);

        //Connect to the Bluetooth service
        Intent gattServiceIntent = new Intent(this, BLEService.class);
        bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, "Ready: service bound");
        Button connectButton = this.findViewById(R.id.connection_manager);
        String buttonText = "Connect to " + device_name;
        connectButton.setText(buttonText);

        int noPermissionCount = 0;	// count up the number app permissions not granted (used to request all remaining permissions at once)

        ArrayList<String> PermissionsList = new ArrayList<>();
        if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, getApplicationContext())) {
            noPermissionCount++;	// increment the count of no permissions
            PermissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        String[] PermissionsString = new String[noPermissionCount];
        PermissionsList.toArray(PermissionsString);
        if (noPermissionCount > 0) {
            ActivityCompat.requestPermissions(this, PermissionsString, PERMISSIONS_REQUEST_CODE);
        }
        chartG = findViewById(R.id.chart1);
        chartM = findViewById(R.id.chart2);

        //reset trial number
        CVtrial = 0;

        BT_Break = false;

        Calendar calendar = new GregorianCalendar();
        String StringFormat = getResources().getString(R.string.file_name_string);
        String Year = calendar.get(Calendar.YEAR) + "";
        String Month = padCalendarItems(calendar.get(Calendar.MONTH)+1);	// months are indexed starting at 0 in Calendar
        String Day = padCalendarItems(calendar.get(Calendar.DAY_OF_MONTH));
        String Hour = padCalendarItems(calendar.get(Calendar.HOUR_OF_DAY));
        String Minute = padCalendarItems(calendar.get(Calendar.MINUTE));
        String Second = padCalendarItems(calendar.get(Calendar.SECOND));
        FileName = String.format(StringFormat, Title, Year, Month, Day, Hour, Minute, Second);
        Log.e(TAG, "File name string: " + FileName);

        scatterChart = findViewById(R.id.scatterChart);
    }
    public static boolean checkPermission(String strPermission,Context c){
        int result = ContextCompat.checkSelfPermission(c, strPermission);
        return result != PackageManager.PERMISSION_GRANTED;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BLEAdapter = ((BLEService.LocalBinder) service).getService();
            BLEAdapter.setActivityHandler(rtHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            BLEAdapter = null;
        }
    };

    //when connect button is pressed
    public void onConnect(View view){
        Log.d(TAG,"onConnect");
        if (BLEAdapter != null){
            if (BLEAdapter.connect(device_address)){
                this.findViewById(R.id.connection_manager).setEnabled(false);
                Log.d(TAG, "Connection successful");
            }
            else {
                Log.d(TAG,"onConnect: failed to connect");
            }
        }
        else {
            Log.d(TAG,"onConnect: BLEAdapter =null");
        }

    }
    public void onAmpButton10(View view ){
        Log.d(TAG, "Amplitude: 0.01 V");
        signalamp = "0.01";

        Button a10 = findViewById(R.id.pulse_amp_10);
        deactivateParameterButton(a10);

        Button a100 = findViewById(R.id.pulse_amp_100);
        activateParameterButton(a100);
    }

    public void onAmpButton100(View view ){
        Log.d(TAG, "Amplitude: 0.1 V");
        signalamp = "0.1";

        Button a100 = findViewById(R.id.pulse_amp_100);
        deactivateParameterButton(a100);

        Button a10 = findViewById(R.id.pulse_amp_10);
        activateParameterButton(a10);
    }
    public void onScanButton12_5(View view) {
        Log.d(TAG, "onScanButton12_5");
        scanrate = "0.0125";
        Button sr10 = findViewById(R.id.scan_button_12_5);
        deactivateParameterButton(sr10);

        Button sr20 = findViewById(R.id.scan_button_25);
        activateParameterButton(sr20);

        Button sr50 = findViewById(R.id.scan_button_50);
        activateParameterButton(sr50);

        Button sr100 = findViewById(R.id.scan_button_100);
        activateParameterButton(sr100);
    }
    public void onScanButton25(View view) {
        Log.d(TAG, "onScanButton20");
        scanrate = "0.025";
        Button sr20 = findViewById(R.id.scan_button_25);
        deactivateParameterButton(sr20);

        Button sr10 = findViewById(R.id.scan_button_12_5);
        activateParameterButton(sr10);

        Button sr50 = findViewById(R.id.scan_button_50);
        activateParameterButton(sr50);

        Button sr100 = findViewById(R.id.scan_button_100);
        activateParameterButton(sr100);
    }

    public void onScanButton50(View view) {
        Log.d(TAG, "onScanButton50");
        scanrate = "0.05";
        Button sr50 = findViewById(R.id.scan_button_50);
        deactivateParameterButton(sr50);

        Button sr10 = findViewById(R.id.scan_button_12_5);
        activateParameterButton(sr10);

        Button sr20 = findViewById(R.id.scan_button_25);
        activateParameterButton(sr20);

        Button sr100 = findViewById(R.id.scan_button_100);
        activateParameterButton(sr100);
    }
    public void onScanButton100(View view) {
        Log.d(TAG, "onScanButton100");
        scanrate = "0.1";
        Button sr100 = findViewById(R.id.scan_button_100);
        deactivateParameterButton(sr100);

        Button sr10 = findViewById(R.id.scan_button_12_5);
        activateParameterButton(sr10);

        Button sr20 = findViewById(R.id.scan_button_25);
        activateParameterButton(sr20);

        Button sr50 = findViewById(R.id.scan_button_50);
        activateParameterButton(sr50);
    }

    public void activateParameterButton(Button butt) {
        butt.setEnabled(true);
        butt.setBackgroundColor(Color.rgb(41, 84, 129));
        butt.setTextColor(Color.WHITE);
    }

    public void deactivateParameterButton(Button butt) {
        butt.setEnabled(false);
        butt.setBackgroundColor(Color.WHITE);
        butt.setTextColor(Color.rgb(41, 84, 129));
    }
    public void onVirusAnalysis(View view) {
        //now used for EIS
        Method = 1;
        Button dTest = findViewById(R.id.start_virus_test);
        dTest.setEnabled(false);
        String commandString = "";	// build the string with commands to set up analysis...
        commandString += "f3";
        commandString += "a";// COMMAND TO ENTER ANALYTICAL SELECTION FUNCTION
        commandString += "e";
        int intervalFrequency = 10; //samples per decade
        // time in seconds to equilibrate at starting potential
        int equilibrationTime = 10;
        //may 31 note: bias voltage should be open circuit potential
        commandString += startf + tab + endf + tab + intervalFrequency + tab + biasvoltage + tab + signalamp + tab + equilibrationTime + tab;
        sendMessage(commandString);
        secondMeasurementIncoming = false;
    }

    public void onCVAnalysis(View view) {
        //now being used for CV
        //may 31 - test 10,20,50,100 for scan rate - do each test on a fresh electrode
        //may 31 - do 3 cycles (first cycle is not reliable)
        Method = 2;
        Button sTest = findViewById(R.id.start_CV_test);
        String commandString = "";
        commandString += "f3"; //3 electrode config
        commandString += "a"; //analytical selection
        commandString += "c"; //cyclic voltammetry
        String startPotential = "-0.1"; //unit: volts
        String endPotential = "0.5"; //unit: volts
        String scanRate = scanrate; //unit: volts/s
        String equilibrationTime = "10";
        commandString += startPotential + tab + endPotential + tab + scanRate + tab + equilibrationTime + tab;
        sendMessage(commandString);
        sTest.setEnabled(false);
        sTest.setText(R.string.waiting);
        CVtrial++;

    }
    public void onExportData(View view){
        SendDataTEST();
    }
    public void onDPVAnalysis(View view) {
        //declare method so we know which type of data we'll be receiving
        Method = 3;
        //disable the DPV button so user knows it has been pressed
        Button dTest = findViewById(R.id.start_dpv_test);
        dTest.setEnabled(false);
        dTest.setText(R.string.waiting);
        //send message to PCB
        String startVoltage = "-0.2"; //Volts
        String endVoltage = "0.5"; //Volts
        String stepFreq = "25";
        String stepPotential = "2"; //mV
        String pulseAmplitude = "25"; //mV
        String equilibrationTime = "10"; //seconds i think?
        String commandString = "";
        commandString += "f3"; //3 electrode config
        commandString += "a"; //enter analytical mode
        commandString += "d"; //dpv
        commandString += startVoltage + tab + endVoltage + tab + stepFreq + tab + stepPotential + tab + pulseAmplitude + tab + equilibrationTime + tab;
        sendMessage(commandString);


    }

    /*
    Try to parse a "double" string; return 1.0 if not a number
     */
    public static double safeParseDouble(String numberString) {
        try {
            return Double.parseDouble(numberString);
        }
        catch (NumberFormatException WTH) {
            return 1.0;
        }
    }

    public void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (BLEAdapter != null){
        if (!BLEAdapter.isConnected()) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }} else{Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show(); return;}
        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothService to write
            byte[] send = message.getBytes();
            Log.d(TAG, "Attempting to send a message. Size of message = " + send.length);
            BLEAdapter.write(send);
        }
    }

    public void onQuit(View view) {
        Log.d(TAG, "onQuit");
        ExitRealTime();
    }

    private String padCalendarItems(int number) {
        String value = number + "";
        if (number < 10) value = "0" + number;
        return value;
    }
    @SuppressLint("HandlerLeak")
    private final Handler rtHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if(D) Log.d(TAG, "Entered real time Handler");
            switch (msg.what) {
               //note - deleted many options and added all options from ABEStatActivity
                case BLEService.GATT_CONNECTED:
                    Log.d(TAG, "messageHandler: Connected");
                    Button connectButton = Real_Time_Activity.this.findViewById(R.id.connection_manager);
                    //connectButton.setBackgroundColor(Color.GREEN);
                    connectButton.setTextColor(Color.rgb(1, 82, 6));
                    connectButton.setBackgroundColor(Color.rgb(211,232,212));
                    String buttonText = "Connected";
                    connectButton.setTextSize(20);
                    connectButton.setText(buttonText);
                    BLEAdapter.discoverServices();
                    break;
                case BLEService.GATT_DISCONNECTED:
                    Log.d(TAG, "messageHandler: Disconnected");
                    connectButton = Real_Time_Activity.this.findViewById(R.id.connection_manager);
                    connectButton.setTextColor(Color.BLACK);
                    connectButton.setBackgroundColor(Color.WHITE);
                    buttonText = "Not connected";
                    connectButton.setText(buttonText);
                    break;
                case BLEService.GATT_CHARACTERISTIC_CHANGED:
                    Log.d(TAG, "messageHandler: GATT characteristic changed");
                    break;
                case BLEService.NOTIF_OR_INDICATION_RECEIVED:
                    Log.d(TAG, "messageHandler: Notification received");
                    break;
                case BLEService.GATT_SERVICES_DISCOVERED:
                    //Validate services
                    List<BluetoothGattService> slist = BLEAdapter.getSupportedGattServices();
                    for (BluetoothGattService svc : slist) {
                        List<BluetoothGattCharacteristic> clist = BLEAdapter.getGattCharacteristics(svc);
                        for (int i = 0; i < clist.size(); i++){
                            BluetoothGattCharacteristic characteristic = clist.get(i);
                            if (characteristic.getUuid().toString().equalsIgnoreCase(BLEService.CHAR3_UUID)) {
                                BLEAdapter.setCharacteristicNotification(characteristic, true);
                            }
                            if (characteristic.getUuid().toString().equalsIgnoreCase(BLEService.CHAR4_UUID)) {
                                BLEAdapter.setCharacteristicNotification(characteristic, true);
                            }
                        }
                    }
                    break;
                case EIS_DATA:
                    Bundle eisBundle = (Bundle) msg.obj;
                    String freq = eisBundle.getString(FREQUENCY_KEY);
                    String zmag = eisBundle.getString(IMPEDANCE_MAGNITUDE_KEY);
                    String zphase = eisBundle.getString(IMPEDANCE_PHASE_KEY);
                    Frequency.add(freq);
                    Z.add(zmag);
                    Zi.add(zphase);
                    double fr = safeParseDouble(freq);
                    if (fr == 100000) {
                        Log.d(TAG, "EIS analysis is done");
                        AnalysisDone();
                    }
                    break;
                case EIS_2_DATA:
                    Bundle eis2Bundle = (Bundle) msg.obj;
                    String freq2 = eis2Bundle.getString(FREQUENCY_KEY);
                    String zmag2 = eis2Bundle.getString(IMPEDANCE_MAGNITUDE_KEY);
                    String zphase2 = eis2Bundle.getString(IMPEDANCE_PHASE_KEY);
                    Frequency2.add(freq2);
                    Z2.add(zmag2);
                    Zi2.add(zphase2);
                    double fr2 = safeParseDouble(freq2);
                    if (fr2 == 100000) {
                        Log.d(TAG, "EIS analysis is done");
                        AnalysisDone();
                    }
                    break;
                case CV_DATA:
                    Bundle dBundle = (Bundle) msg.obj;
                    String pot = dBundle.getString(POTENTIAL_KEY);
                    String cur = dBundle.getString(CURRENT_KEY);
                    CV_potential.add(pot);
                    CV_current.add(cur);
                    break;
                case CV_2_DATA:
                    Bundle d2Bundle = (Bundle) msg.obj;
                    String pot2 = d2Bundle.getString(POTENTIAL_KEY);
                    String cur2 = d2Bundle.getString(CURRENT_KEY);
                    CV_potential_2.add(pot2);
                    CV_current_2.add(cur2);
                    break;
                case DPV_DATA:
                    Bundle dpvBundle = (Bundle) msg.obj;
                    String DPV_p = dpvBundle.getString(POTENTIAL_KEY);
                    String DPV_c = dpvBundle.getString(CURRENT_KEY);
                    DPV_potential.add(DPV_p);
                    DPV_current.add(DPV_c);
                    break;
                case DPV_2_DATA:
                    Bundle dpv2Bundle = (Bundle) msg.obj;
                    String DPV2_p = dpv2Bundle.getString(POTENTIAL_KEY);
                    String DPV2_c = dpv2Bundle.getString(CURRENT_KEY);
                    DPV_potential_2.add(DPV2_p);
                    DPV_current_2.add(DPV2_c);
                    break;
                case CYCLE_COMPLETE:
                    AnalysisDone();
                    break;
                default:
                    break;
            }
        }
    };
    private void AnalysisDone() {
        Button testButton;
        switch (Method) {
            case 1:
                testButton = findViewById(R.id.start_virus_test);
                testButton.setEnabled(true);
                testButton.setText(R.string.nxt_trial);
                testButton.setBackgroundColor(Color.rgb(211,232,212));
                chartData();
                break;
            case 2:
                testButton = findViewById(R.id.start_CV_test);
                testButton.setEnabled(true);
                testButton.setText(R.string.nxt_trial);
                testButton.setBackgroundColor(Color.rgb(211,232,212));
                chartData();
                break;
            case 3:
                testButton = findViewById(R.id.start_dpv_test);
                testButton.setEnabled(true);
                testButton.setText(R.string.nxt_trial);
                testButton.setBackgroundColor(Color.rgb(211,232,212));
                chartData();
                break;
            default:
                Log.e(TAG, "Testing type not one of expected results");
                break;
        }

    }
    @Override
    public void onBackPressed() {
        ExitDialog();//showDialog(QUIT_DIALOG); // show the quit dialog
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth services, and waypoint timer, notifications, etc...
        unbindService(serviceConnection);
        BLEAdapter = null;
    }

    public void onXXX(View view){
        sendMessage(EXIT_STRING);
    }
    private void ExitRealTime() {
        sendMessage(EXIT_STRING);	// send character instruction to exit real time routine on BioRanger (go to stand-by mode)- send it at least 16 times in case device is hung up in data display routine
        //Check_Saving_Options();
        // make sure this message is not displayed if user has not checked "Save Data" box
        Log.e(TAG, "removed callbacks to RFU Sentinel");
       //release_wakelock();
        Log.e(TAG, "Cancel countdown timer");
        loggingTimer.cancel();
        //Intent quitIntent = new Intent();
        //quitIntent.putExtra(FILE_SAVED, File_Saved);
        //quitIntent.putExtra(SAVE_FILE_CHECK, Save_Data_Check.isChecked());	// if "Save File" is not checked, don't toast external storage not available
        if (BLEAdapter != null) {
            BLEAdapter.disconnect();
        }
        BLEAdapter = null;
        Log.e(TAG, "BTService is disconnected...");
        //quitIntent.putExtra(THREAD_INTERRUPTED, Thread_Interrupted);
        Log.e(TAG, "initiated quit intent, and finish loading intent data");
        //setResult(Activity.RESULT_OK, quitIntent);
        finish();
    }

    private void ExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.quit_query)
                .setPositiveButton(R.string.yes, (dialog, whichButton) -> ExitRealTime())
                .setNegativeButton(R.string.no, (dialog, whichButton) -> dialog.dismiss())
                .create()
                .show();
    }


    private void chartData() {
        Log.d(TAG, "Chart data");
        scatterChart.setVisibility(View.VISIBLE);
        chartM.setVisibility(View.INVISIBLE);
        chartG.setVisibility(View.INVISIBLE);
        ArrayList<Entry> scatterEntries = new ArrayList<>();
        XAxis x_axis = scatterChart.getXAxis();
        YAxis y_axis_L = scatterChart.getAxisLeft();
        YAxis y_axis_R = scatterChart.getAxisRight();
        x_axis.setEnabled(true);
        y_axis_L.setEnabled(true);
        y_axis_R.setEnabled(false);
        switch (Method) {
            case 1: //EIS
                for (int i = 0; i < Frequency.size(); i++) {
                    try {
                        float x = Float.parseFloat(Frequency.get(i));
                        float y = Float.parseFloat(Z.get(i));
                        Entry entry = new Entry(scaleCbr(x),scaleCbr(y));
                        Log.d(TAG, "New entry: " + x + ", " + y);
                        scatterEntries.add(entry); } catch(Exception e) {
                        Log.e(TAG, "Data at index " + i + " is no good");
                    }
                }
                //Add units for frequency and convert to log
                y_axis_L.setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        return unScaleCbr(value)/ 1000 + "kΩ";
                        //int A = (int) value;
                        //return A / 1000 + "kΩ";
                    }
                });
                x_axis.setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        return unScaleCbr(value) + "Hz";
                        //int A = (int) value;
                        //return A / 1000 + "kΩ";
                    }
                });
                x_axis.setAxisMinimum(scaleCbr(0.01));
                x_axis.setAxisMaximum(scaleCbr(100000));
                x_axis.setLabelCount(8, true);
                break;
            case 2: //CV
                for (int i = 0; i < CV_potential.size(); i++) {
                    try {
                        float x = Float.parseFloat(CV_potential.get(i));
                        float y = Float.parseFloat(CV_current.get(i));
                        Entry entry = new Entry(x,y);
                        Log.d(TAG, "New entry: " + x + ", " + y);
                        scatterEntries.add(entry); } catch(Exception e) {
                        Log.e(TAG, "Data at index " + i + " is no good");
                    }
                }
            case 3: //DPV
                for (int i = 0; i < DPV_potential.size(); i++) {
                    try {
                        float x = Float.parseFloat(DPV_potential.get(i));
                        float y = Float.parseFloat(DPV_current.get(i));
                        Entry entry = new Entry(x,y);
                        Log.d(TAG, "New entry: " + x + ", " + y);
                        scatterEntries.add(entry); } catch(Exception e) {
                        Log.e(TAG, "Data at index " + i + " is no good");
                    }
                }
            default:
                float x = 0.0f;
                float y = 0.0f;
                Entry entry = new Entry(x,y);
                scatterEntries.add(entry);
                Log.e(TAG, "Something wrong -- method not expected");
                break;
        }
        scatterDataSet = new ScatterDataSet(scatterEntries, "");
        scatterDataSet.setColors(ColorTemplate.JOYFUL_COLORS);
        scatterDataSet.setDrawValues(false);
        scatterDataSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
        scatterData = new ScatterData(scatterDataSet);
        scatterChart.getLegend().setEnabled(false);
        scatterChart.setData(scatterData);
        Description desc = new Description();
        desc.setText("Test results");
        scatterChart.setDescription(desc);
        scatterChart.invalidate();

        Log.d(TAG, "scatter data set");

    }
//FROM https://github.com/PhilJay/MPAndroidChart/issues/2768
    private float scaleCbr(double cbr) {
        return (float)(Math.log10(cbr));
    }
    //FROM https://github.com/PhilJay/MPAndroidChart/issues/2768
    private float unScaleCbr(double cbr) {
        double calcVal = Math.pow(10, cbr);
        return (float)(calcVal);
    }

    private void SendDataTEST() {

        StringBuilder strbldr = new StringBuilder();
        if (Method == 1){
            Log.d(TAG, "Send data test called. Size of EIS arrays: " + Frequency.size() + tab + Frequency2.size());
            try {
                if (Frequency2.size() == 0) {
                    strbldr.append("F (Hz), Z (Ohms), Zi").append('\n');
                    for (int i = 0; i < Frequency.size(); i++) {
                        strbldr.append(Frequency.get(i)).append(',').append(Z.get(i)).append(',').append(Zi.get(i)).append('\n');
                    }
                }
                else if (Frequency.size() == 0) {
                    strbldr.append("F (Hz), Z (Ohms), Zi").append('\n');
                    for (int i = 0; i < Frequency2.size(); i++) {
                        strbldr.append(Frequency2.get(i)).append(',').append(Z2.get(i)).append(',').append(Zi2.get(i)).append('\n');
                    }
                }
                else if (Frequency2.size() < Frequency.size()){
                    strbldr.append("F1 (Hz), Z1 (Ohms), Zi1, F2 (Hz), Z2 (Ohms), Zi2").append('\n');
                    for (int i = 0; i < Frequency2.size(); i++) {
                        strbldr.append(Frequency.get(i)).append(',').append(Z.get(i)).append(',').append(Zi.get(i)).append(',');
                        strbldr.append(Frequency2.get(i)).append(',').append(Z2.get(i)).append(',').append(Zi2.get(i)).append('\n');
                    }
                    strbldr.append(Frequency.get(Frequency.size()-1)).append(',').append(Z.get(Frequency.size()-1)).append(',').append(Zi.get(Frequency.size()-1));
                }
                else {
                    strbldr.append("F1 (Hz), Z1 (Ohms), Zi1, F2 (Hz), Z2 (Ohms), Zi2").append('\n');
                    for (int i = 0; i < Frequency.size(); i++) {
                        strbldr.append(Frequency.get(i)).append(',').append(Z.get(i)).append(',').append(Zi.get(i)).append(',');
                        strbldr.append(Frequency2.get(i)).append(',').append(Z2.get(i)).append(',').append(Zi2.get(i)).append('\n');
                    }
                    strbldr.append(Frequency2.get(Frequency2.size()-1)).append(',').append(Z2.get(Frequency2.size()-1)).append(',').append(Zi2.get(Frequency2.size()-1));

                }
            } catch (Exception e) {
                Log.e(TAG, "Exception trying to fill string builder");
            }
        }
        else if (Method == 2) {
            Log.d(TAG, "Send data test called. Size of CV arrays: " + CV_potential.size() + tab + CV_potential_2.size());
            try {
                strbldr.append("E1 (V), I1 (nA), E2 (V), I2 (nA)").append('\n');
                if (CV_potential_2.size() == 0 ){
                    for (int i = 0; i < CV_potential.size(); i++) {
                        strbldr.append(CV_potential.get(i)).append(',').append(CV_current.get(i)).append('\n');
                    }
                }
                else if (CV_potential.size() == 0) {
                    for (int i = 0; i < CV_potential_2.size(); i++) {
                        strbldr.append(CV_potential_2.get(i)).append(',').append(CV_current_2.get(i)).append('\n');
                    }
                }
                else if (CV_potential_2.size() < CV_potential.size()) {
                    for (int i = 0; i < CV_potential_2.size(); i++) {
                        strbldr.append(CV_potential.get(i)).append(',').append(CV_current.get(i)).append(',');
                        strbldr.append(CV_potential_2.get(i)).append(',').append(CV_current_2.get(i)).append('\n');
                    }
                } else {
                    for (int i = 0; i < CV_potential.size(); i++) {
                    strbldr.append(CV_potential.get(i)).append(',').append(CV_current.get(i)).append(',');
                    strbldr.append(CV_potential_2.get(i)).append(',').append(CV_current_2.get(i)).append('\n');
                    }
                }
            } catch(Exception e) {
                Log.e(TAG, "Exception trying to fill string builder");
            }
        }
        else if (Method == 3) {
            Log.d(TAG, "Send data test called. Size of DPV arrays: " + DPV_potential.size() + tab + DPV_potential_2.size());
            strbldr.append("E1 (V), I1 (nA), E2 (V), I2 (nA)").append('\n');
            try {
                if (DPV_potential_2.size() == 0 ){
                    for (int i = 0; i < DPV_potential.size(); i++) {
                        strbldr.append(DPV_potential.get(i)).append(',').append(DPV_current.get(i)).append('\n');
                    }
                }
                else if (DPV_potential.size() == 0) {
                    for (int i = 0; i < DPV_potential_2.size(); i++) {
                        strbldr.append(DPV_potential_2.get(i)).append(',').append(DPV_current_2.get(i)).append('\n');
                    }
                }
                else if (DPV_potential_2.size() < DPV_potential.size()) {
                    for (int i = 0; i < DPV_potential_2.size(); i++) {
                        strbldr.append(DPV_potential.get(i)).append(',').append(DPV_current.get(i)).append(',');
                        strbldr.append(DPV_potential_2.get(i)).append(',').append(DPV_current_2.get(i)).append('\n');
                    }
                } else {
                    for (int i = 0; i < DPV_potential.size(); i++) {
                        strbldr.append(DPV_potential.get(i)).append(',').append(DPV_current.get(i)).append(',');
                        strbldr.append(DPV_potential_2.get(i)).append(',').append(DPV_current_2.get(i)).append('\n');
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception trying to fill string builder");
            }
        }
        String sfromb = strbldr.toString();

        Intent send = new Intent();
        send.setAction(Intent.ACTION_SEND);
        send.putExtra(Intent.EXTRA_TEXT, sfromb);
        send.setType("text/plain");

        Intent share = Intent.createChooser(send, null);
        startActivity(share);
    }

}
