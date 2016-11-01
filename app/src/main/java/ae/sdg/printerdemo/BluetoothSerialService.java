/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ae.sdg.printerdemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.ganesh.iarabic.arabic864;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothSerialService {
    // Debugging
    private static final String TAG = "BluetoothReadService";
    private static final boolean D = true;

    // Message types sent from the BluetoothReadService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";


    private static final UUID SerialPortServiceClass_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    //declaration for in main class
    public arabic864 araconvert=null;


    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothSerialService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
        //instance for Arabic under onCreate
        araconvert = new arabic864();
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");


        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        setState(STATE_NONE);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        setState(STATE_NONE);

        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(SerialPortServiceClass_UUID);
            } catch (IOException e) {
                Log.e(TAG, "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                connectionFailed();
                Log.e(TAG, "unable to close() socket during connection failure", e);
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                // Start the service over to restart listening mode
                //BluetoothSerialService.this.start();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothSerialService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;


        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        /*
         * Method edited by Ahmed Alabadi
         * 08 Sep 2016
         */
        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;
            
//            try {
////                mmOutStream.write("\u001bw(          ".getBytes());
////                String font8 = CHAR_ESC + CHAR_K + HEX_8 + CHAR_CR + "Ajman DED \n" + CHAR_LF;
////                String font1 = CHAR_ESC + CHAR_K + HEX_1 + CHAR_CR + "دائرة عجمان الاقاتصادية ";
////                mmOutStream.write(font8.getBytes());
////                mmOutStream.write("Ajman DED".getBytes());
////                mmOutStream.write("دائرة عجمان الاقاتصادية ".getBytes());
////                mmOutStream.write(font1.getBytes());
////                mmOutStream.write("\r\n\r\n Test Invoice: tested by Ahmed Alabadi\r\n                    +971504417033\r\n\r\n\r\n             Ajman DED\r\n             P.O. Box 870,\r\n             Ajman,\r\n             United Arab Emirates\r\n             Toll Free : 800 70\r\n\r\n\r\n--------------------------------------------\r\n\r\n\r\n        Thank you\r\n        Ajman DED\r\n\r\n\r\n\r\n".getBytes());
//                mmOutStream.write((
//                        "Hello World"
////                                " Receipt No.:  2335454\r\n" +
////                                " Date :        AM \t10:45 \t  \t08/08/2016\r\n" +
////                                "--------------------------------------------\r\n" +
////                                "          Notice irregularities\r\n" +
////                                "Name: Frahat Restaurant\r\n" +
////                                "No.:  2324\r\n" +
////                                "--------------------------------------------\r\n" +
////                                "--------------------------------------------\r\n" +
////                                "The department inspector visited the facility and found following irregularities\r\n" +
////                                "--------------------------------------------\r\n" +
////                                "Practicing an economic activity without a license or permit         ->> 5000.00 AED\r\n" +
////                                "Non-compliance with the conditions of the permit                    ->> 3000.00 AED\r\n" +
////                                "The exercise of an economic activity not contained in the license   ->> 3000.00 AED\r\n" +
////                                "--------------------------------------------\r\n" +
////                                "Total: 9000.00 AED\r\n" +
////                                "--------------------------------------------\r\n" +
////                                "--------------------------------------------\r\n" +
////                                "Please pay irregularities at the earliest to prevent any delay fines or the closure of the facility\r\n" +
////                                "--------------------------------------------\r\n" +
////                                "          Ajman DED\r\n" +
////                                "Phone: 800 70\r\n" +
////                                "Email: info@ajmanded.ae\r\n"
//
//
//                ).getBytes());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

//                    mEmulatorView.write(buffer, bytes);
                    // Send the obtained bytes to the UI Activity
                    //mHandler.obtainMessage(BlueTerm.MESSAGE_READ, bytes, -1, buffer).sendToTarget();

                    String a = buffer.toString();
                    a = "";
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {

//                //switching to Eazy mode for placing the texts in right place of receipt
//                byte[] easymode = new byte[] {0x1b,0x45,0x5a};
//                mmOutStream.write(easymode);
//                //start of text printing
//                // Y = Vertical line number/position
//                // X = Horizontal position of the text
//                //PRINT is called once before printing rows with positions
//                //should end with curly bracket }. Look at the bottom of this code
//                // Since Arabic conversion output is byte array
//                //all the string commands are sent split and sent as bytes
//                int Y=10;
//                int X=40;
//                String easyinit = "{PRINT:";
//                mmOutStream.write(easyinit.getBytes());
//                //ARABT is the Unicode font loaded in PR3
//                String lineseq1="@"+ Y +"," + X +":ARABT,HMULT2,VMULT2|";
//                mmOutStream.write(lineseq1.getBytes());
//                mmOutStream.write("Sample Receipt".getBytes());
//                mmOutStream.write("|".getBytes());
//                //change the horizontal position only for printing Arabic on the right
//                X=490;
//                String lineseq2="@"+ Y +"," + X +":ARABT,HMULT2,VMULT2|";
//                mmOutStream.write(lineseq2.getBytes());
//                mmOutStream.write(araconvert.Convert("استلام العينة",true));
//                mmOutStream.write("|".getBytes());
//                //Ending curly bracket for {PRINT in the beginning
//                mmOutStream.write("}".getBytes());
//                // changing from eazy mode to line print mode
//                mmOutStream.write("{LP}".getBytes());
                
                // Arabfont select arabic font(ESC w q). q--> is the ID font reported by the printer once the arabic font TTF has been loaded.
                byte[] arabFont = new byte[]{0x1B, 0x77, 0x48, 0x0D};
                byte[] arabChar = new byte[]{(byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xed, (byte) 0xee, 0x0D};

                //Printing example using a downloaded arabic font "Traditional Arabic (0x71) " and codepage 864
                mmOutStream.write(araconvert.Convert("استلام العينة",true));
//                mmOutStream.write("مثال عربى".getBytes());
                mmOutStream.write("\n".getBytes());
                mmOutStream.write(arabFont);

                mmOutStream.write(arabChar);
                String testArabString = ("بيتر زاهر");
//                String Reversed = new StringBuffer(testArabString).reverse().toString();
//                byte[] reservedByteArray = Reversed.getBytes();
//                mmOutStream.write(reservedByteArray);

                mmOutStream.write("\n\n".getBytes("Cp1256"));
                mmOutStream.write(buffer);
                mmOutStream.write(araconvert.Convert(testArabString,true));
                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, buffer.length, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
