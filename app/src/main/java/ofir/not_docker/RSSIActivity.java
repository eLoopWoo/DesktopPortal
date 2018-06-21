package ofir.not_docker;




import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static android.R.attr.data;

public class RSSIActivity extends Activity {
    public static final String TAG = "BT application";
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    static private String ip;
    static private String port;
    static private TextView message;
    final int MESSAGE_READ = 9999; // its only identifier to tell to handler what to do with data you passed through.



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rssi);
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        Button button = (Button) findViewById(R.id.button1);
         message = (TextView) findViewById(R.id.message);

        //start bluetooth activity
        button.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mBluetoothAdapter != null) {
                    mBluetoothAdapter.startDiscovery();
                } else {
                    Toast.makeText(RSSIActivity.this, "Device has no bluetooth",
                            Toast.LENGTH_LONG).show();
                }
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }


            }
        });

        //start playing video
        //"rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov"
        VideoView videoView;
        videoView = (VideoView) this.findViewById(R.id.videoView);
        VideoThread vthread = new VideoThread(videoView, "rtsp://184.72.239.149/vod/mp4:BigBuckBunny_175k.mov");
        vthread.run();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_rssi, menu);
        return true;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //create a device
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //get name and address
                String deviceHardwareAddress = device.getAddress(); // MAC address
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                //get rssi and insert it to
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                TextView rssi_msg = (TextView) findViewById(R.id.textView1);
                ConnectThread thread = new ConnectThread(device);
                thread.run();

                rssi_msg.setText(rssi_msg.getText() + name + "[" + deviceHardwareAddress + "]" + " => " + rssi + "dBm\n");
                Toast.makeText(RSSIActivity.this, rssi_msg.getText() + name + " => " + rssi + "dBm\n",
                        Toast.LENGTH_LONG).show();
            }
        }



    };

    @Override
    protected void onDestroy() {
        super.onDestroy();


        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
    }



    public static void OnDataReceive(byte[] data, int length) {
         int msg_num = 0;
        String packet = "";
        if(msg_num == 0){
            for(int i=0; i < length; i++){
                packet += String.valueOf(Integer.parseInt(String.format("%02X", data[i]), 16)) + ".";
            }
            packet = packet.substring(0, packet.length() - 1);
            ip = packet;
        }
        if(msg_num == 1){
            for(int i=0; i < length; i++){
                packet += String.valueOf(Integer.parseInt(String.format("%02X", data[i]), 16));
            }
            port = packet;

        }
        Log.d(TAG, message.getText().toString() +", " +  packet);
        message.setText(message.getText().toString() +", " +  packet);
        msg_num++;

    }
    //a class that establishs a device connection

    private class VideoThread extends Thread{
            VideoView videoView;
            String videoRtspUrl;
        public VideoThread( VideoView videoView, String url){
            //video stream

            videoRtspUrl=url;
            this.videoView = videoView;
            videoView.setVideoPath(videoRtspUrl);


        }
        public void run(){
            videoView.requestFocus();
            videoView.start();
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;






        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                Log.d(TAG, "trying to connect to " + device.getName());
                UUID MY_UUID = UUID.fromString("aaaaaaaa-5555-4444-3333-bbbbbbbbbbbb");

                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            //mBluetoothAdapter.cancelDiscovery();

            try {
                Log.d(TAG, "establishing socket");
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.

            MyBluetoothService.ConnectedThread manageSocket = new MyBluetoothService().new ConnectedThread(mmSocket);
            Log.d(TAG, "running socket manager thread");
            manageSocket.run();
            //start communicating

        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }






    //bluetooth servier class

    public static class MyBluetoothService {
        private static final String TAG = "MY_APP_DEBUG_TAG";
        private Handler mHandler; // handler that gets info from Bluetooth service

        // Defines several constants used when transmitting messages between the
        // service and the UI.
        private interface MessageConstants {
            public static final int MESSAGE_READ = 0;
            public static final int MESSAGE_WRITE = 1;
            public static final int MESSAGE_TOAST = 2;

            // ... (Add other message types here as needed.)
        }

        private class ConnectedThread extends Thread {
            private final BluetoothSocket mmSocket;
            private final InputStream mmInStream;
            private final OutputStream mmOutStream;
            private byte[] mmBuffer; // mmBuffer store for the stream

            public ConnectedThread(BluetoothSocket socket) {
                mmSocket = socket;
                InputStream tmpIn = null;
                OutputStream tmpOut = null;

                // Get the input and output streams; using temp objects because
                // member streams are final.
                try {
                    tmpIn = socket.getInputStream();
                } catch (IOException e) {
                    Log.e(TAG, "Error occurred when creating input stream", e);
                }
                try {
                    tmpOut = socket.getOutputStream();
                } catch (IOException e) {
                    Log.e(TAG, "Error occurred when creating output stream", e);
                }

                mmInStream = tmpIn;
                mmOutStream = tmpOut;
            }

            public void run() {
                mmBuffer = new byte[1024];
                int numBytes; // bytes returned from read()
                Log.d(TAG, "listening for socket messages");
                // Keep listening to the InputStream until an exception occurs.
                while (true) {
                    try {
                        // Read from the InputStream.
                        numBytes = mmInStream.read(mmBuffer);
                        OnDataReceive(mmBuffer, numBytes);
                    } catch (IOException e) {
                        Log.d(TAG, "Input stream was disconnected", e);
                        break;
                    }
                }
            }

            // Call this from the main activity to send data to the remote device.
            public void write(byte[] bytes) {
                try {
                    mmOutStream.write(bytes);

                    // Share the sent message with the UI activity.
                    Message writtenMsg = mHandler.obtainMessage(
                            MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                    writtenMsg.sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "Error occurred when sending data", e);

                    // Send a failure message back to the activity.
                    Message writeErrorMsg =
                            mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                    Bundle bundle = new Bundle();
                    bundle.putString("toast",
                            "Couldn't send data to the other device");
                    writeErrorMsg.setData(bundle);
                    mHandler.sendMessage(writeErrorMsg);
                }
            }

            // Call this method from the main activity to shut down the connection.
            public void cancel() {
                try {
                    mmSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close the connect socket", e);
                }
            }
        }




    }


}