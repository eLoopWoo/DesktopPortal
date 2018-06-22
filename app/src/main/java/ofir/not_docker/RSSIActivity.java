package ofir.not_docker;




import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import android.Manifest;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.net.Uri;
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
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static android.R.attr.data;
import static android.R.attr.left;

public class RSSIActivity extends Activity   {

    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }
    public static volatile  boolean RUN_SOCKET = true;
    public static final String TAG = "BT application";
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private String ip;
    private int port;
    static private TextView message;
    static public ImageView screenShot;
    private HashMap< BluetoothDevice, Integer> bt_list = new HashMap< BluetoothDevice, Integer>();
    private static int msg_num = 0;
    private Map<BluetoothDevice ,Integer > treeMap;
    final int MESSAGE_READ = 9999; // its only identifier to tell to handler what to do with data you passed through.

    private static int shakeTime1 = 0;


    private SensorManager mSensorManager;

    private ShakeEventListener mSensorListener;

    private static final int IMAGE_TRANSFER = 1;
    private static final int FILE_TRANSFER = 2;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rssi);

        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            //your codes here

        }
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number




        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorListener = new ShakeEventListener();

        mSensorListener.setOnShakeListener(new ShakeEventListener.OnShakeListener() {

            public void onShake() {

                int currTime =(int) (System.currentTimeMillis());
                if(currTime-shakeTime1 < 2000){
                    shakeTime1 = currTime;
                    Log.d(TAG,"Already shaken");
                }else{
                    Log.d(TAG, "Device was shaken");
                    shakeTime1 = currTime;
                    RefreshConnections();
                }

            }
        });



        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        screenShot = (ImageView) findViewById(R.id.screenshot);
        Button button = (Button) findViewById(R.id.button1);
         message = (TextView) findViewById(R.id.message);

        //start bluetooth activity
        button.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
            RefreshConnections();
            }
        });

    }




    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        mSensorManager.unregisterListener(mSensorListener);
        super.onPause();
    }



    public void RefreshConnections(){
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

        //stop scanning devices and try streamng to the strongest one
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //stop scanning after 5 seconds
                mBluetoothAdapter.cancelDiscovery();

                //sort bt map
                Comparator<BluetoothDevice> comparator = new ValueComparator(bt_list);
                //TreeMap is a map sorted by its keys.
                //The comparator is used to sort the TreeMap by keys.
                treeMap  = new TreeMap<BluetoothDevice, Integer>(comparator);
                treeMap.putAll(bt_list);


                ConnectToClosestBT();

            }
        }, 5000);

    }

    public void ConnectToClosestBT(){
        if(treeMap.entrySet().iterator().hasNext()){
            Map.Entry< BluetoothDevice , Integer> entry = treeMap.entrySet().iterator().next();
            Log.d(TAG, "Connecting to device " + entry.getKey().getName() + " with RSSI "
                    + entry.getValue());
            ConnectThread thread = new ConnectThread(entry.getKey());
            thread.run();
        }else{
            Log.d(TAG, "devices are empty, should restart descovery");
        }
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
                bt_list.put( device, rssi);



                rssi_msg.setText(rssi_msg.getText() + name + "[" + deviceHardwareAddress + "]" + " => " + rssi + "dBm\n");

            }
        }



    };

    @Override
    protected void onDestroy() {
        super.onDestroy();


        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
    }

    public void OnFileTransfer(File file){
        Log.d(TAG, "Received file");
        P2PThread p2PThread = new P2PThread(FILE_TRANSFER, file);
        p2PThread.start();
    }


    public void OnBTDataReceive(byte[] data, int length) {
        String packet = "";

        if(msg_num == 0){
            for(int i=0; i < length; i++){
                packet += String.valueOf(Integer.parseInt(String.format("%02X", data[i]), 16)) + ".";
            }
            packet = packet.substring(0, packet.length() - 1);
            ip = packet;
        }
        if(msg_num == 1){
            int lower_port = Integer.parseInt(String.format("%02X", data[0]), 16);
            int upper_port = Integer.parseInt(String.format("%02X", data[1]), 16);

            port = (lower_port | (upper_port<<8));
            Log.d(TAG, "port in message receive is :" );
            packet = String.valueOf(port);


        }
        Log.d(TAG, message.getText().toString() +", " +  packet);
        final String p = packet;
        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                message.setText(message.getText().toString() +", " +  p);
            }
        });

        msg_num++;
        if(msg_num ==2){
            Log.d(TAG, "starting p2p thread");
            RUN_SOCKET = true;
            P2PThread p2pThread = new P2PThread(IMAGE_TRANSFER, null);
            p2pThread.run();
        }
    }
    //a class that establishs a device connection
    private class P2PThread extends Thread{
        int protocolCode;
        File file;
        public  P2PThread(int protocolCode, File file) {
            this.protocolCode = protocolCode;
            this.file = file;
        }
        public void run () {
            listenSocket(ip, port, protocolCode, file);

        }
    }
    public  void listenSocket(String ip, int port, int protocolCode, File file){
//Create socket connection

        try{
            Log.d(TAG,"ip is: " + ip + " port is : " + port);
            Socket socket = new Socket(ip,port);
            //set up driver
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            Log.d(TAG, "socket listen is " + String.valueOf(RUN_SOCKET));

            out.writeByte(protocolCode);

            if(protocolCode == IMAGE_TRANSFER){
                while (RUN_SOCKET) {

                    //Log.d(TAG, "listening...");
                    try {
                        byte[] bb = new byte[4];
                        in.read(bb, 0, 4);
                        int b1 =  Integer.parseInt(String.format("%02X", bb[0]), 16);
                        int b2 =  Integer.parseInt(String.format("%02X", bb[1]), 16);
                        int b3 =  Integer.parseInt(String.format("%02X", bb[2]), 16);
                        int b4 =  Integer.parseInt(String.format("%02X", bb[3]), 16);

                        int length = b4;
                        length = length << 8;
                        length += b3;
                        length = length << 8;
                        length += b2;
                        length = length << 8;
                        length += b1;
                        //  Log.d(TAG, "image length is:" + length);

                        byte[] imageb = new byte[length];
                        int CHUNK_SIZE = 4096;
                        int already_read = 0;
                        while (already_read < length) {
                            int left_to_read = length - already_read >= CHUNK_SIZE ?
                                    CHUNK_SIZE : length - already_read;
                            already_read += in.read(imageb, already_read, left_to_read);
                        }
//                    Log.d(TAG, "already_read: " + already_read);

                        final Bitmap bmp = BitmapFactory.decodeByteArray(imageb, 0, length);


                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                screenShot.setImageBitmap(Bitmap.createScaledBitmap(bmp, bmp.getWidth(),
                                        bmp.getHeight(), false));

                            }
                        });

                    } catch (IOException e) {
                        Log.d(TAG, "Read failed");
                        break;
                        //System.exit(1);
                    }
                }
            }else{
                if(protocolCode == FILE_TRANSFER){
                    // Returns the contents of the file in a byte array.
                    try {
                        // Get the size of the file
                        long length = file.length();

                        // You cannot create an array using a long type.
                        // It needs to be an int type.
                        // Before converting to an int type, check
                        // to ensure that file is not larger than Integer.MAX_VALUE.
                        if (length > Integer.MAX_VALUE) {
                            // File is too large
                            throw new IOException("File is too large!");
                        }
                        //send length through socket
                        out.writeLong(length);
                        String name = file.getName();

                        //write name length to the file
                        out.write(name.length());

                        //write file name
                        byte[] fileName = name.getBytes();
                        out.write(fileName);


                        // Create the byte array to hold the data
                        byte[] bytes = new byte[(int)length];

                        // Read in the bytes
                        int offset = 0;
                        int numRead = 0;

                        InputStream is = new FileInputStream(file);
                        int len;
                        while ((len = in.read(bytes)) != -1) {
                            out.write(bytes, 0, len);
                        }
                        is.close();
                        // Ensure all the bytes have been read in
                        if (offset < bytes.length) {
                            throw new IOException("Could not completely read file "+file.getName());
                        }


                    }catch (IOException e){
                        Log.e(TAG, "file I/O exception" + e.getMessage());

                    }
                }
                out.close();
                in.close();
            }

            
            if(!socket.isClosed()){
                socket.close();

            }
        } catch (UnknownHostException e) {
            Log.d(TAG, "Unknown host: " + ip);
            //System.exit(1);
        } catch  (IOException e) {
            Log.d(TAG, "No I/O");
            //System.exit(1);
        }
    }

    //create a
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;





        //create socket with bt device
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
                    Log.d(TAG, "device those not provide necessery service");
                    treeMap.remove(mmDevice);
                    ConnectToClosestBT();
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
            manageSocket.start();
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

    public class MyBluetoothService {
        private Handler mHandler; // handler that gets info from Bluetooth service

        // Defines several constants used when transmitting messages between the
        // service and the UI.


        private class ConnectedThread extends Thread {
            private final BluetoothSocket mmSocket;
            private final InputStream mmInStream;
            private final OutputStream mmOutStream;
            private byte[] mmBuffer; // mmBuffer store for the stream

            public ConnectedThread(BluetoothSocket socket) {
                mmSocket = socket;
                InputStream tmpIn = null;
                OutputStream tmpOut = null;
                msg_num = 0;
                RUN_SOCKET = false;
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
                        OnBTDataReceive(mmBuffer, numBytes);
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