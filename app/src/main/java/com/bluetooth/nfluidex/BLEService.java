package com.bluetooth.nfluidex;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;

import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.Context;

import android.os.Handler;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;


import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BLEService extends Service {

    private static final String TAG = "BLEService";
    private BluetoothAdapter btadapter;
    private BluetoothGatt btgatt;
    private BluetoothManager btmanager;
    private Handler activity_handler = null;
    private BluetoothDevice btdevice;
    private boolean connected = false;

    private connectionThread cThread;
    private PipedOutputStream pOutput;
    private PipedInputStream pInput = new PipedInputStream();


    //GATT = generic attribute profile
    public static final int GATT_CONNECTED = 1;
    public static final int GATT_DISCONNECTED = 2;
    public static final int GATT_SERVICES_DISCOVERED = 3;
    public static final int MESSAGE = 4;
    public static final int NOTIF_OR_INDICATION_RECEIVED = 5;
    public static final int GATT_CHARACTERISTIC_CHANGED = 6;
    //public static final int GATT_SUCCESS = 7;


    public static final String DESCRIPTOR = "DESCRIPTOR_UUID";
    public static final String CHAR = "CHARACTERISTIC_UUID";
    public static final String SERVICE = "SERVICE_UUID";
    public static final String VALUE = "VALUE";
    public static final String TEXT = "TEXT";

    public static String SERVICE3_UUID = "65333333-A115-11E2-9E9A-0800200CA100";
    public static String SERVICE4_UUID = "65333333-A115-11E2-9E9A-0800200CA200";
    public static String CHAR3_UUID = "65333333-A115-11E2-9E9A-0800200CA101";
    public static String CHAR4_UUID = "65333333-A115-11E2-9E9A-0800200CA202";

    //testing for callback
    public static boolean sending = false;
    public static boolean incomingF = false;
    public static boolean incomingEIS2 = false;
    public static boolean incomingD = false;
    public static boolean incomingD2 = false;
    public static boolean incomingDPV = false;
    public static boolean incomingDPV2 = false;

    private ConcurrentLinkedQueue<BLEOperation> queue = new ConcurrentLinkedQueue<com.bluetooth.nfluidex.BLEService.BLEOperation>();
    private com.bluetooth.nfluidex.BLEService.BLEOperation pendingOperation = null;
    boolean DEVELOPER_MODE = true;

    public void setActivityHandler(Handler handler) {
        activity_handler = handler;
    }

    public boolean isConnected() {
        return connected;
    }

    @Override
    public void onCreate() {
        if (btmanager == null) {
            btmanager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (btmanager == null) {
                Log.d(TAG, "btmanager is null");
                return;
            }
        }
        btadapter = btmanager.getAdapter();
        if (btadapter == null) {
            Log.d(TAG, "onCreate: Bluetooth Adapter is null");
        }
    }

    //inside service class, initialize IBinder w/ object created by inner class
    private final IBinder binder = new LocalBinder();

    public void write(byte[] send) {
        BluetoothGattCharacteristic characteristic = btgatt.getService(UUID.fromString(SERVICE3_UUID)).getCharacteristic(UUID.fromString(CHAR3_UUID));
        writeCharacteristic(characteristic, send);
    }

    //inner class
    public class LocalBinder extends Binder {
        public BLEService getService() {
            return BLEService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    private void sendConsoleMessage(String text) {
        Message msg = Message.obtain(activity_handler, MESSAGE);
        Bundle data = new Bundle();
        data.putString(TEXT, text);
        msg.setData(data);
        msg.sendToTarget();
    }

    public boolean connect(final String address) {
        if (btadapter == null || address == null) {
            sendConsoleMessage("connect: btadapter = null");
            return false;
        }
        btdevice = btadapter.getRemoteDevice(address);
        if (btdevice == null) {
            sendConsoleMessage("connect: device=null");
            return false;
        }
        btgatt = btdevice.connectGatt(this, false, gatt_callback);
        return true;
    }

    public void disconnect() {
        sendConsoleMessage("disconnecting");
        if (btadapter == null || btgatt == null) {
            sendConsoleMessage("disconnect:btadapter|btgatt = null");
            return;
        }
        if (btgatt != null) {
            btgatt.disconnect();
        }
    }

    //------------CLASS FOR QUEUE------------
    public static class BLEOperation {
        BluetoothDevice device;
    }

    public static class CharacteristicWrite extends BLEOperation {
        BluetoothGattCharacteristic characteristic;
        byte[] byteArray;

        CharacteristicWrite(BluetoothGattCharacteristic characteristic, byte[] byteArray) {
            this.characteristic = characteristic;
            this.byteArray = byteArray;
        }

        public BluetoothGattCharacteristic getCharacteristic() {
            return this.characteristic;
        }

        public byte[] getByteArray() {
            return this.byteArray;
        }
    }

    public static class SetCharacteristicNotification extends BLEOperation {
        BluetoothGattCharacteristic characteristic;
        boolean enabled;

        SetCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
            this.characteristic = characteristic;
            this.enabled = enabled;
        }

        public BluetoothGattCharacteristic getCharacteristic() {
            return this.characteristic;
        }

        public boolean isEnabled() {
            return this.enabled;
        }
    }

    public static class CharacteristicChange extends BLEOperation {
        BluetoothGattCharacteristic characteristic;

        CharacteristicChange(BluetoothGattCharacteristic characteristic){
            this.characteristic = characteristic;
        }
        public BluetoothGattCharacteristic getCharacteristic(){return this.characteristic;}
    }

    public synchronized void enqueueOperation(com.bluetooth.nfluidex.BLEService.BLEOperation operation) {
        queue.add(operation);
        Log.d(TAG, "operation has been added to queue");
        if (pendingOperation == null) {
            doNextOperation();
        }
    }

    public synchronized void doNextOperation() {
        if (pendingOperation != null) {
            Log.e(TAG, "doNextOperation called when operation pending");
            return;
        }
        BLEOperation operation = queue.poll();
        if (operation == null) {
            Log.e(TAG, "doNextOperation called when queue is empty");
            return;
        }
        pendingOperation = operation;
        if (operation instanceof CharacteristicChange){

        }
        if (operation instanceof SetCharacteristicNotification) {
            BluetoothGattCharacteristic characteristic = ((SetCharacteristicNotification) operation).getCharacteristic();
            boolean enabled = ((SetCharacteristicNotification) operation).isEnabled();
            String descriptor_UUID_String = "00002902-0000-1000-8000-00805f9b34fb";
            UUID descriptor_UUID = UUID.fromString(descriptor_UUID_String);

            btgatt.setCharacteristicNotification(characteristic, enabled);
            if (CHAR3_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptor_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    boolean result;
                    result = btgatt.writeDescriptor(descriptor);
                    if (!result) {
                        Log.d(TAG, "ERROR: btgatt.writeDescriptor 3 failed");
                    } else {
                        Log.d(TAG, "setCharacteristicNotification: set and enable indication value for CHAR3");
                    }
                } else {
                    Log.d(TAG, "setCharacteristicNotification: characteristic.getDescriptor failed");
                }
            }
            if (CHAR4_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(descriptor_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    boolean result;
                    result = btgatt.writeDescriptor(descriptor);
                    if (!result) {
                        Log.d(TAG, "ERROR: btgatt.writeDescriptor 4 failed");
                    } else {
                        Log.d(TAG, "setCharacteristicNotification: set and enable indication value for CHAR4");
                    }
                } else {
                    Log.d(TAG, "setCharacteristicNotification: characteristic.getDescriptor failed");
                }
            }
        }

        if (operation instanceof CharacteristicWrite) {
            BluetoothGattCharacteristic characteristic = ((CharacteristicWrite) operation).getCharacteristic();
            byte[] byteArray = ((CharacteristicWrite) operation).getByteArray();
            boolean tooLong;
            tooLong = (byteArray.length > 20);
            boolean result;
            StringBuilder fullMessage = new StringBuilder(100);
            if (tooLong) {
                byte[] m1 = new byte[20];
                System.arraycopy(byteArray, 0, m1, 0, 20);
                characteristic.setValue(m1);
                for (byte b : m1) {
                    fullMessage.append((char) b);
                }
            } else {
                characteristic.setValue(byteArray);
                for (byte b : byteArray) {
                    fullMessage.append((char) b);
                }
            }
            result = btgatt.writeCharacteristic(characteristic);
            if (!result) {
                Log.d(TAG, "doNextOperation: writeCharacteristic: btgatt.writeCharacteristic FAILED. Attempted message: " + fullMessage);
            } else {
                Log.d(TAG, "doNextOperation: writeCharacteristic: btgatt.writeCharacteristic worked! Message sent: " + fullMessage);
                if (tooLong) {
                    byte[] m2 = new byte[byteArray.length - 20];
                    System.arraycopy(byteArray, 20, m2, 0, byteArray.length - 20);
                    CharacteristicWrite nextMessage = new CharacteristicWrite(characteristic, m2);
                    enqueueOperation(nextMessage);
                }
            }
        }
    }

    public synchronized void signalEndOfOperation() {
        pendingOperation = null;
        if (!queue.isEmpty()) {
            doNextOperation();
        }
    }
    //------------END OF QUEUE STUFF---------
    public synchronized void stop(){
        cThread.StopThread = true;
    }

    private final BluetoothGattCallback gatt_callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: status=" + status);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "onConnectionStateChange: CONNECTED");
                connected = true;
                Message msg = Message.obtain(activity_handler, GATT_CONNECTED);
                msg.sendToTarget();
                cThread =  new connectionThread();
                cThread.start();
                try {
                    pOutput = new PipedOutputStream(pInput);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "onConnectionStateChange: DISCONNECTED");
                stop();
                connected = false;
                Message msg = Message.obtain(activity_handler, GATT_DISCONNECTED);
                msg.sendToTarget();
                if (btgatt != null) {
                    Log.d(TAG, "Destroying BluetoothGatt object");
                    btgatt.close();
                    btgatt = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            sendConsoleMessage("Services discovered");
            Message msg = Message.obtain(activity_handler, GATT_SERVICES_DISCOVERED);
            msg.sendToTarget();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            //below was added feb 18 to try threads and piped input/output streams
            try {
                pOutput.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            StringBuilder sb = new StringBuilder();
            for (byte datum : data) {
                char x = (char) datum;
                sb.append(x);
            }
            Log.d(TAG, "new message:" + sb);
            //commented out below when added thread - cThread should check for new info in message
            //try {
            //    readEISdata(message);
           // } catch (IOException e) {
            //    e.printStackTrace();
          //  }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
            Log.d(TAG, "onDescriptorWrite: parent characteristic = " + parentCharacteristic.getUuid().toString());
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "ERROR: Write descriptor failed");
            } else {
                //byte[] value = descriptor.getValue();
                Log.d(TAG, "onDescriptorWrite succeeded");
                if (pendingOperation instanceof SetCharacteristicNotification) {
                    signalEndOfOperation();
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            Log.d(TAG, "onCharacteristicWrite");
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "ERROR: characteristic write failed. Error = " + status);
            } else {
                sending = false;
                Log.d(TAG, "Characteristic write succeeded!!");
                if (pendingOperation instanceof CharacteristicWrite) {
                    signalEndOfOperation();
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            boolean success = (status == BluetoothGatt.GATT_SUCCESS);
            Log.d(TAG, "ATT MTU changed to " + mtu + ", success = " + success);
        }
    };

    public void discoverServices() {
        if (btadapter == null || btgatt == null) {
            return;
        }
        Log.d(TAG, "Discovering GATT services");
        btgatt.discoverServices();
        Log.d(TAG, "Services discovered");
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (btgatt == null) {
            return null;
        }
        return btgatt.getServices();
    }

    public List<BluetoothGattCharacteristic> getGattCharacteristics(BluetoothGattService service) {
        if (service == null) {
            return null;
        }
        return service.getCharacteristics();
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {

        if (btgatt == null) {
            Log.d(TAG, "setGattCharacteristicNotification: BTgatt is null");
            return;
        }
        SetCharacteristicNotification notif = new SetCharacteristicNotification(characteristic, enabled);
        enqueueOperation(notif);

    }

    // maybe combine write and writeCharacteristic so we wait until positive result before attempting to send next message
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] byteArray) {
        if (btgatt == null) {
            Log.d(TAG, "writeCharacteristic: BTgatt is null");
            return;
        }
        if (byteArray != null) {
            if (hasExpectedProperties(characteristic)) {
                CharacteristicWrite message = new CharacteristicWrite(characteristic, byteArray);
                enqueueOperation(message);
                Log.d(TAG, "writeCharacteristic: Added message to queue");
            } else {
                Log.d(TAG, "writeCharacteristic: characteristic passed does not have expected properties");
            }
        } else {
            Log.d(TAG, "writeCharacteristic: byteArray is null");
        }
    }

    public boolean hasExpectedProperties(BluetoothGattCharacteristic characteristic) {
        int prop = characteristic.getProperties();
        return prop == 40;
    }


    //these two methods are not currently being used but might be useful in future
   /*public boolean requestMtu(int mtu){
        boolean result;
        result = btgatt.requestMtu(mtu);
        if (result){
            Log.d(TAG, "requestMtu success");
        }
        else{Log.d(TAG, "requestMtu fail");}
        return result;
    }

    public boolean canWrite(){
        BluetoothGattCharacteristic characteristic = btgatt.getService(UUID.fromString(SERVICE3_UUID)).getCharacteristic(UUID.fromString(CHAR3_UUID));
        BluetoothGattDescriptor desc3 = characteristic.getDescriptor(UUID.fromString(CHAR3_UUID));
        return true;
    }*/

    private class connectionThread extends Thread{
        private boolean StopThread = false;
        private PipedInputStream pInput;

        //constructor
        public connectionThread(){
            Log.d(TAG, "created connectionThread");
            pInput = BLEService.this.pInput;
        }

        public void run(){
            Log.d(TAG, "connectionThread beginning");
            while(!StopThread){
                try {
                    if (connected){
                        if (pInput == null) return;
                        if (pInput.available() > 0) {
                            readEISdata(pInput);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public void readEISdata(InputStream stream) throws IOException {
            Bundle DataBundle = new Bundle();
            int byter = stream.read();
            char instr = (char) byter;

            if (instr == 'f' &&  Real_Time_Activity.Method == 1) {
                incomingF = true;
                Log.d(TAG, "Reading EIS data");
                String f = read(stream);
                Log.d(TAG, "String f= " + f);
                String z = read(stream);
                Log.d(TAG,"String z = " + z);
                String p = read(stream);
                Log.d(TAG, "String p = " + p);
                DataBundle.putString(Real_Time_Activity.FREQUENCY_KEY, f);
                DataBundle.putString(Real_Time_Activity.IMPEDANCE_MAGNITUDE_KEY, z);
                DataBundle.putString(Real_Time_Activity.IMPEDANCE_PHASE_KEY, p);
                incomingF = false;
                activity_handler.obtainMessage(Real_Time_Activity.EIS_DATA, DataBundle).sendToTarget();
            }
            else if (instr == 'p' && Real_Time_Activity.Method == 1) {
                incomingEIS2 = true;
                Log.d(TAG, "Reading EIS2 data");
                String f2 = read(stream);
                Log.d(TAG, "String f2 = " + f2);
                String z2 = read(stream);
                Log.d(TAG, "String z2 = " + z2);
                String p2 = read(stream);
                Log.d(TAG, "String p2 = " + p2);
                DataBundle.putString(Real_Time_Activity.FREQUENCY_KEY, f2);
                DataBundle.putString(Real_Time_Activity.IMPEDANCE_MAGNITUDE_KEY, z2);
                DataBundle.putString(Real_Time_Activity.IMPEDANCE_PHASE_KEY, p2);
                incomingEIS2 = false;
                activity_handler.obtainMessage(Real_Time_Activity.EIS_2_DATA, DataBundle).sendToTarget();
            }
            //CV data is coming
            else if (instr == 'd' && Real_Time_Activity.Method == 2){
                incomingD = true;
                Log.d(TAG, "Reading CV data");
                String p = read(stream);
                Log.d(TAG, "String p = " + p);
                String c = read(stream);
                Log.d(TAG, "String c = " + c);
                DataBundle.putString(Real_Time_Activity.POTENTIAL_KEY, p);
                DataBundle.putString(Real_Time_Activity.CURRENT_KEY, c);
                incomingD = false;
                activity_handler.obtainMessage(Real_Time_Activity.CV_DATA, DataBundle).sendToTarget();
            }
            else if (instr == 'd' && Real_Time_Activity.Method == 3){
                incomingDPV = true;
                Log.d(TAG, "Reading DPV data");
                //DPV data comes back the same as CV
                String p = read(stream);
                Log.d(TAG, "String p = " + p);
                String c = read(stream);
                Log.d(TAG, "String c = " + c);
                DataBundle.putString(Real_Time_Activity.POTENTIAL_KEY, p);
                DataBundle.putString(Real_Time_Activity.CURRENT_KEY, c);
                incomingDPV = false;
                activity_handler.obtainMessage(Real_Time_Activity.DPV_DATA, DataBundle).sendToTarget();
            }
            else if (instr == 'p' && Real_Time_Activity.Method == 2){
                incomingD2 = true;
                Log.d(TAG, "Reading CV2 data");
                String p2 = read(stream);
                Log.d(TAG, "String p = " + p2);
                String c2 = read(stream);
                Log.d(TAG, "String c = " + c2);
                DataBundle.putString(Real_Time_Activity.POTENTIAL_KEY, p2);
                DataBundle.putString(Real_Time_Activity.CURRENT_KEY, c2);
                incomingD2 = false;
                activity_handler.obtainMessage(Real_Time_Activity.CV_2_DATA, DataBundle).sendToTarget();
            }
            else if (instr == 'p' && Real_Time_Activity.Method == 3){
                incomingDPV2 = true;
                Log.d(TAG, "Reading DPV2 data");
                //DPV data comes back the same as CV
                String p = read(stream);
                Log.d(TAG, "String p = " + p);
                String c = read(stream);
                Log.d(TAG, "String c = " + c);
                DataBundle.putString(Real_Time_Activity.POTENTIAL_KEY, p);
                DataBundle.putString(Real_Time_Activity.CURRENT_KEY, c);
                incomingDPV2 = false;
                activity_handler.obtainMessage(Real_Time_Activity.DPV_2_DATA, DataBundle).sendToTarget();
            }
            else if (instr == 'c' && Real_Time_Activity.Method == 2){
                Log.d(TAG,"CV cycle complete");
                Message msg = Message.obtain(activity_handler, Real_Time_Activity.CYCLE_COMPLETE);
                msg.sendToTarget();
            }
            else if (instr == 'c' && Real_Time_Activity.Method == 3){
                Log.d(TAG,"DPV cycle complete");
                Message msg = Message.obtain(activity_handler, Real_Time_Activity.CYCLE_COMPLETE);
                msg.sendToTarget();
            }

        }
        public String read(InputStream stream) throws IOException {
            StringBuilder data = new StringBuilder();
            int byter;
            char character;
            if (incomingF || incomingEIS2 || incomingD || incomingDPV || incomingD2 || incomingDPV2) {
                do {
                    // first, wait for data to become available on InStream (assumes this function is only called when data is expected)
                    // note that the simple call to InStream.read function returns an abstract integer, that must be recast as a character...
                    //while (message.available() == 0) ;
                    // then read byte...
                    byter = stream.read();
                    character = (char) byter;
                    // and tack read byte to end of data string it is not the terminal character
                    if (character != '\t') data.append(character);

                } while (character != '\t');
                // as long as the terminal character /t is not read...
            }
            return data.toString();
        }
    }
}
