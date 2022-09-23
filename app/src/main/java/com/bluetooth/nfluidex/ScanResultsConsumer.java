package com.bluetooth.nfluidex;

import android.bluetooth.BluetoothDevice;

//must be implemented by MainActivity so it can receive and process data
//interface is group of related methods with empty bodies
//interfaces can be implemented by a class
public interface ScanResultsConsumer {
    public void candidateDevice(BluetoothDevice device, byte[] scan_record, int rssi);
    public void scanningStarted();
    public void scanningStopped();
}
