package ae.sdg.printerdemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ganesh.iarabic.arabic864;

import static ae.sdg.printerdemo.BluetoothSerialService.*;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private BluetoothAdapter mBluetoothAdapter = null;
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    private static BluetoothSerialService mSerialService = null;

    //Views
    TextView mStatusTextView;
    Button mConnectButton, mSendButton;
    EditText mOrderEditText;

    //declaration for in main class
    public arabic864 araconvert=null;

    private boolean mEnablingBT;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //instance for Arabic under onCreate
        araconvert = new arabic864();

        mStatusTextView = (TextView) findViewById(R.id.status_text_view);
        mConnectButton = (Button) findViewById(R.id.connect_button);
        mSendButton = (Button) findViewById(R.id.send_button);
        mOrderEditText = (EditText) findViewById(R.id.order_edit_text);

        mConnectButton.setOnClickListener(this);
        mSendButton.setOnClickListener(this);

        mOrderEditText.setText("! 0 200 200 210 1\n" +
                "TEXT 4 0 30 40 Hello World\n" +
                "FORM\n" +
                "PRINT");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            finishDialogNoBluetooth();
        }
        mSerialService = new BluetoothSerialService(this, mHandlerBT);
    }

    @Override
    public void onStart() {
        super.onStart();
        mEnablingBT = false;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if (!mEnablingBT) { // If we are turning on the BT we cannot check if it's enable
            if ((mBluetoothAdapter != null) && (!mBluetoothAdapter.isEnabled())) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.alert_dialog_turn_on_bt)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.alert_dialog_warning_title)
                        .setCancelable(false)
                        .setPositiveButton(R.string.alert_dialog_yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                mEnablingBT = true;
                                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                            }
                        })
                        .setNegativeButton(R.string.alert_dialog_no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finishDialogNoBluetooth();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }

            if (mSerialService != null) {
                // Only if the state is STATE_NONE, do we know that we haven't started already
                if (mSerialService.getState() == BluetoothSerialService.STATE_NONE) {
                    // Start the Bluetooth chat services
                    mSerialService.start();
                }
            }

            if (mBluetoothAdapter != null) {

            }
        }
    }

    public int getConnectionState() {
        return mSerialService.getState();
    }

    public void send(byte[] out) {
        mSerialService.write(out);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSerialService != null)
            mSerialService.stop();

    }

    // The Handler that gets information back from the BluetoothService
    private final Handler mHandlerBT = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothSerialService.STATE_CONNECTED:
//                            if (mMenuItemConnect != null) {
//                                mMenuItemConnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
//                                mMenuItemConnect.setTitle(R.string.disconnect);
//                            }

                            mStatusTextView.setText(getString(R.string.title_connected_to));
                            mStatusTextView.append(mConnectedDeviceName);
                            mConnectButton.setText(R.string.disconnect);
//                            mInputManager.showSoftInput(mEmulatorView, InputMethodManager.SHOW_IMPLICIT);

//                            mTitle.setText(R.string.title_connected_to);
//                            mTitle.append(mConnectedDeviceName);
                            break;

                        case BluetoothSerialService.STATE_CONNECTING:
                            mStatusTextView.setText(R.string.title_connecting);
                            break;

                        case BluetoothSerialService.STATE_LISTEN:
                        case BluetoothSerialService.STATE_NONE:
//                            if (mMenuItemConnect != null) {
//                                mMenuItemConnect.setIcon(android.R.drawable.ic_menu_search);
//                                mMenuItemConnect.setTitle(R.string.connect);
//                            }
//
//                            mInputManager.hideSoftInputFromWindow(mEmulatorView.getWindowToken(), 0);
                            mStatusTextView.setText(R.string.title_not_connected);
                            mConnectButton.setText(R.string.connect);

                            break;
                    }
                    break;
                case MESSAGE_WRITE:
//                    if (mLocalEcho) {
//                        byte[] writeBuf = (byte[]) msg.obj;
//                        mEmulatorView.write(writeBuf, msg.arg1);
//                    }

                    break;
/*
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                mEmulatorView.write(readBuf, msg.arg1);
                break;
*/
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public void finishDialogNoBluetooth() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_dialog_no_bt)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.app_name)
                .setCancelable(false)
                .setPositiveButton(R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_CONNECT_DEVICE:

                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    // Attempt to connect to the device
                    mSerialService.connect(device);
                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    finishDialogNoBluetooth();
                }
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id){
            case R.id.connect_button:
                if (getConnectionState() == BluetoothSerialService.STATE_NONE) {
                    // Launch the DeviceListActivity to see devices and do scan
                    Intent serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                } else if (getConnectionState() == BluetoothSerialService.STATE_CONNECTED) {
                    mSerialService.stop();
                    mSerialService.start();
                }
                break;
            case R.id.send_button:
                String command = mOrderEditText.getText().toString();
                if (!command.isEmpty())
                    send(araconvert.Convert(command, true));
                break;
        }
    }
}
