package com.bluetooth.nfluidex;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

//Perform scanning when called
//Provide details of BT devices found to MainActivity through ScanResultsConsumer
public class Scanner {
    /*
    BluetoothLeScanner gives methods to perform scan related tasks
     */
    private BluetoothLeScanner scanner = null;
    /*
    BluetoothAdapter
    represents local BT adapter, allows you to perform basic tasks
    call BluetoothManager.getAdapter() to get
     */
    private BluetoothAdapter bluetooth_adapter = null;
    /*
    Handler lets you send and process messages
     */
    private Handler handler = new Handler();

    private ScanResultsConsumer scan_results_consumer;

    private Context context;
    private boolean scanning = false;
    private String device_name_start="";
    private final String TAG = "Scanner";

    //constructor
    //needs to take in context so we can start an activity
    public Scanner(Context context) {
        this.context = context;

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        bluetooth_adapter = bluetoothManager.getAdapter();

        //check bluetooth is available and on
        if (bluetooth_adapter == null || !bluetooth_adapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is NOT switched on");
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBTIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBTIntent);
        }
        Log.d(TAG, "Bluetooth is switched on");
    }

    //start scanning method
    public void startScanning(final ScanResultsConsumer scan_results_consumer, long stop_after_ms) {
        if (scanning) {
            Log.d(TAG, "Already scanning so ignore startScanning request");
            return;
        }
        if (scanner == null) {
            scanner = bluetooth_adapter.getBluetoothLeScanner();
            Log.d(TAG, "Created BluetoothScanner object");
        }
        //runnable created is added to message queue, run after specified time elapses
        handler.postDelayed(() -> {
            if (scanning) {
                Log.d(TAG, "Stopping scanning");
                scanner.stopScan(scan_callback);
                setScanning(false);

            }
        }, stop_after_ms);

        this.scan_results_consumer = scan_results_consumer;
        //set up scan filter
        Log.d(TAG, "Scanning");
        List<ScanFilter> filters;
        filters = new ArrayList<ScanFilter>();

        //this type of scanning is aggressive,uses lots of battery
        //might be a better option for our uses
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        setScanning(true);
        scanner.startScan(filters, settings, scan_callback);

    }

    public void stopScanning(){
        setScanning(false);
        Log.d(TAG,"Stopping scanning");
        scanner.stopScan(scan_callback);
    }

    private ScanCallback scan_callback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!scanning) {
                return;
            }
            //call method from scan results consumer interface
            //main activity implements scan results consumer
            scan_results_consumer.candidateDevice(result.getDevice(), result.getScanRecord().getBytes(), result.getRssi());
        }
    };

    //getter and setter methods
    public boolean isScanning() {
        return scanning;
    }

    //want the boolean to actually do something/update scan results consumer
    void setScanning(boolean scanning){
        this.scanning = scanning;
        //stop scanning
        if (!scanning){
            scan_results_consumer.scanningStopped();
        }
        //start scanning
        else{
            scan_results_consumer.scanningStarted();
        }
    }


}

