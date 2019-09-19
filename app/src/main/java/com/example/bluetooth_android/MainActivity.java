package com.example.bluetooth_android;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;


import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static com.example.bluetooth_android.R.drawable;
import static com.example.bluetooth_android.R.id;
import static com.example.bluetooth_android.R.layout;
import static com.example.bluetooth_android.R.string.Connecting;
import static com.example.bluetooth_android.R.string.bluetooth_not_supported;
import static com.example.bluetooth_android.R.string.dont_connect;
import static com.example.bluetooth_android.R.string.please_wait;
import static com.example.bluetooth_android.R.string.start_search;
import static com.example.bluetooth_android.R.string.stop_search;
import java.lang.*;


public class MainActivity<crc> extends AppCompatActivity implements


        CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemClickListener,
        View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int REQUEST_CODE_LOC = 1;

    private static final int REQ_ENABLE_BT = 10;
    public static final int BT_BOUNDED = 21;
    public static final int BT_SEARCH = 22;
    public static final int LED_RED = 30;
    public static final int LED_GREEN = 31;


  //  private final byte[] pcBlock = new byte[0];
  //  private final int len = 0;


    //------------------------------структура передаваемого сообщения (msgid 1) radio_data1_s
    class radio_data1_s {

       // uint8_t dt_format; // формат календаря 10 или 16
       // uint8_t dt_error;  // код ошибки часов
       //ds1307::ds1307_map_t dt; //часы, календарь (7 байт)
       // uint8_t bm_error; //код ошибки bmp280
        //float int_temp;//внутренняя температура град (bmp280)
        //float press; //атмосферное давление мм рт ст (bmp280)
        //uint8_t ds_error; //код ошибки ds18b20
        //float ext_temp;//внешняя температура град (das18b20)

        Short dt_format, dt_error, t_bm_error, ds_error;
        Float ds1307, ext_temp;


    }

    class radio_cmd_s
            //--------------------------структура команды (msgid 5) radio_cmd_s
    {
        short target_id, cmd, len;
        byte[] dat = new byte[17];

        //    uint8_t target_id; // идентификатор получателя команды
        //    uint8_t cmd;       // команда
        //    uint8_t len;       // число доп байт в команде
        //    uint8_t dat[17];   // данные


    }

    class radio_cmd_resp_s
            //-----------------------ответ на команду (msgid 6) radio_cmd_resp_s
            // команда
            // результат операции
            // дополнительные данные
    {
        short cmd, res;
        byte[] dat = new byte[17];
    }

    class radio_frame_s {
             /*-----------------------------структура передаваемых данных radio_frame_s
             uint16_t stx;//стартовое слово 0xa544
             uint8_t crc; //контрольная сумма всего сообщения с солью в зависимости от msgid
             uint8_t len;//длина поля данных
             uint8_t seq;//счетчик пакетов
             uint8_t sysid;// ид отправителя
             uint8_t m1sgid;//тип сообщения
             uint8_t data[];//данные максимум 50 байт если hc12
              */

        int stx, crc, radio_frame_s_len, seq, sysid, m1sgid;
        short aa, bb, cc;
        int[] data = new int[50];
        float a_float;
    }

    private FrameLayout frameMessage;
    private LinearLayout frameControls;


    private RelativeLayout frameLedControls;
    private Button btnDisconnect;
    private Switch switchRedLed;
    private Switch switchGreenLed;
    private EditText etConsole;
    private EditText etChars;

    private Switch switchEnableBt;
    private Button btnEnableSearch;
    private ProgressBar pbProgress;
    private ListView listBtDevices;

    private BluetoothAdapter bluetoothAdapter;
    private BtListAdapter listAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;

    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private ProgressDialog progressDialog;


    byte[] extra_tab = new byte[]{(byte) ((char) 0x0), (byte) ((char) 0x0), (byte) ((char) 0x0), (byte) ((char) 0xfa)};


    char[] extra_tab_char = new char[]{128, 0x00, 127, 0x00};
    private Object crc8;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);

        frameMessage = findViewById(id.frame_message);
        frameControls = findViewById(id.frame_control);

        switchEnableBt = findViewById(id.switch_enable_bt);
        btnEnableSearch = findViewById(id.btn_enable_search);
        pbProgress = findViewById(id.pb_progress);
        listBtDevices = findViewById(id.lv_bt_device);

        frameLedControls = findViewById(id.frameLedControls);
        btnDisconnect = findViewById(id.btn_disconnect);
        switchGreenLed = findViewById(id.switch_led_green);
        switchRedLed = findViewById(id.switch_led_red);
        etConsole = findViewById(R.id.et_console);
        etChars = findViewById(id.et_chars);

        switchEnableBt.setOnCheckedChangeListener(this);
        btnEnableSearch.setOnClickListener(this);
        listBtDevices.setOnItemClickListener(this);

        btnDisconnect.setOnClickListener(this);
        switchGreenLed.setOnCheckedChangeListener(this);
        switchRedLed.setOnCheckedChangeListener(this);

        bluetoothDevices = new ArrayList<>();

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(getString(Connecting));
        progressDialog.setMessage(getString(please_wait));


        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "onCreate: " + getString(bluetooth_not_supported));
            finish();
        }

        if (bluetoothAdapter.isEnabled()) {
            showFrameControls();
            switchEnableBt.setChecked(true);
            setListAdapter(BT_BOUNDED);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);
        if (connectThread != null) {
            connectThread.cancel();
        }

        if (connectedThread != null) {
            connectedThread.cancel();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onClick(View v) {
        if (v.equals(btnEnableSearch)) {
            enableSearch();
        } else if (v.equals(btnDisconnect)) {
            //Отключение от устройства
            if (connectedThread != null) {
                connectedThread.cancel();
            }
            if (connectThread != null) {
                connectThread.cancel();
            }

            showFrameControls();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.equals(listBtDevices)) {
            BluetoothDevice device = bluetoothDevices.get(position);
            if (device != null) {
                connectThread = new ConnectThread(device);
                connectThread.start();
            }
        }


    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.equals(switchEnableBt)) {
            enableBt(isChecked);

            if (!isChecked) {
                showFrameMessage();
            }
        } else if (buttonView.equals(switchRedLed)) {
            // TODO включение или отключение красного светодиода

            enableLed(extra_tab, 0, 4);

        } else if (buttonView.equals(switchGreenLed)) {
            // TODO включение или отключение зелёного светодиода
            // enableLed(extra_tab,0x5, 0x5);
            radio_frame_s t = new radio_frame_s();
            extra_tab[3] = (byte) 0x66;
            extra_tab[2] = (byte) 0xE6;
            extra_tab[1] = (byte) 0xF6;
            extra_tab[0] = (byte) 0x42;
            //  t.a_float = Float.parseFloat("19.95");

            t.a_float = ByteBuffer.wrap(extra_tab).getFloat(0);
            Log.d(TAG, "Тест " + t.a_float);
            Log.d(TAG, "Тест " + t.crc);
            Log.d(TAG, "Тест " + t.radio_frame_s_len);
            Log.d(TAG, "Тест " + t.seq);
            Log.d(TAG, "Тест " + t.sysid);
            Log.d(TAG, "Тест " + t.m1sgid);


        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE_BT) {
            if (resultCode == RESULT_OK && bluetoothAdapter.isEnabled()) {
                showFrameControls();
                setListAdapter(BT_BOUNDED);
            } else if (resultCode == RESULT_CANCELED) {
                enableBt(true);
            }
        }
    }

    public byte[] charsToBytes(char[] chars) {
        Charset charset = Charset.forName("UTF-8");
        ByteBuffer byteBuffer = charset.encode(CharBuffer.wrap(chars));
        return Arrays.copyOf(byteBuffer.array(), byteBuffer.limit());
    }

    public char[] bytesToChars(byte[] bytes) {
        Charset charset = Charset.forName("UTF-8");
        CharBuffer charBuffer = charset.decode(ByteBuffer.wrap(bytes));
        return Arrays.copyOf(charBuffer.array(), charBuffer.limit());
    }

    private int crc8(byte[] pcBook, int len) {
        int i = 0, i1;

        int crc = 0xFF;

        for (i1 = 0; i1 < len; i1++) {

            crc ^= pcBook[i1];

            for (i = 0; i < 8; i++) {
                if ((crc & 0x80) == 0x80) crc = ((crc << 1) ^ 0x31);
                else crc = crc << 1;
            }

        }
        return crc;
    }

    private void radio_pool() {
        int ukz;
        float tst;


    }

    private void showFrameMessage() {
        frameMessage.setVisibility(View.VISIBLE);
        frameLedControls.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
    }

    private void showFrameControls() {
        frameMessage.setVisibility(View.GONE);
        frameLedControls.setVisibility(View.GONE);
        frameControls.setVisibility(View.VISIBLE);
    }

    private void showFrameLedControls() {
        frameLedControls.setVisibility(View.VISIBLE);
        frameMessage.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
    }

    private void enableBt(boolean flag) {
        if (flag) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQ_ENABLE_BT);
        } else {
            bluetoothAdapter.disable();
        }
    }

    private void setListAdapter(int type) {

        bluetoothDevices.clear();
        int iconType = drawable.ic_bluetooth_bounded_device;

        switch (type) {
            case BT_BOUNDED:
                bluetoothDevices = getBoundedBtDevices();
                iconType = drawable.ic_bluetooth_bounded_device;
                break;
            case BT_SEARCH:
                iconType = drawable.ic_bluetooth_search_device;
                break;
        }
        listAdapter = new BtListAdapter(this, bluetoothDevices, iconType);
        listBtDevices.setAdapter(listAdapter);
    }

    private ArrayList<BluetoothDevice> getBoundedBtDevices() {
        Set<BluetoothDevice> deviceSet = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> tmpArrayList = new ArrayList<>();
        if (deviceSet.size() > 0) {
            tmpArrayList.addAll(deviceSet);
        }

        return tmpArrayList;
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void enableSearch() {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        } else {
            accessLocationPermission();
            bluetoothAdapter.startDiscovery();
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            assert action != null;
            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    btnEnableSearch.setText(stop_search);
                    pbProgress.setVisibility(View.VISIBLE);
                    setListAdapter(BT_SEARCH);
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    btnEnableSearch.setText(start_search);
                    pbProgress.setVisibility(View.GONE);
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        bluetoothDevices.add(device);
                        listAdapter.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };

    /**
     * Запрос на разрешение данных о местоположении (для Marshmallow 6.0)
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void accessLocationPermission() {


        int accessCoarseLocation = ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION);
        int accessFineLocation = ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION);

        List<String> listRequestPermission = new ArrayList<>();

        if (accessCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(ACCESS_COARSE_LOCATION);
        }
        if (accessFineLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(ACCESS_FINE_LOCATION);
        }

        if (!listRequestPermission.isEmpty()) {
            String[] strRequestPermission = listRequestPermission.toArray(new String[0]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.requestPermissions(strRequestPermission, REQUEST_CODE_LOC);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // TODO - Add your code here to start Discovery
        if (requestCode == REQUEST_CODE_LOC) {
            if (grantResults.length > 0) for (int gr : grantResults) {
                // Check if request is granted or not
                if (gr != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
        }
    }

    private class ConnectThread extends Thread {


        private BluetoothSocket bluetoothSocket = null;
        private boolean success = false;

        public ConnectThread(BluetoothDevice device) {
            try {
                Method method = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                bluetoothSocket = (BluetoothSocket) method.invoke(device, 1);
                progressDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                bluetoothSocket.connect();
                success = true;
                progressDialog.dismiss();
            } catch (IOException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        Toast.makeText(MainActivity.this, getString(dont_connect), Toast.LENGTH_SHORT).show();
                    }
                });

                cancel();
            }

            if (success) {
                connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showFrameLedControls();
                    }
                });
            }

        }

        public boolean isConnect() {
            return bluetoothSocket.isConnected();
        }


        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectedThread extends Thread {


        private final BluetoothSocket bluetoothSocket=null;
        private InputStream inputStream = null;
        private OutputStream outputStream = null;
        private byte[] bytes; //store for the stream
        private boolean isConnected = false;

        public ConnectedThread(BluetoothSocket bluetoothSocket){
            InputStream tmpIn   = null;
            OutputStream tmpOut = null;
            try{
                tmpIn=bluetoothSocket.getInputStream();
                tmpOut=bluetoothSocket.getOutputStream();
            } catch (IOException e){
                e.printStackTrace();
            }
            this.inputStream=tmpIn;
            this.outputStream=tmpOut;

            isConnected = true;
        }
        // Вызов из Майн Активити и отправка данных в у даленное устройство

        @Override
        public void run(){
            BufferedInputStream bis = new BufferedInputStream(inputStream);

            StringBuffer buffer = new StringBuffer();
            final StringBuffer sbConsole = new StringBuffer();
            final ScrollingMovementMethod movementMethod = new ScrollingMovementMethod();


            while (isConnected){
                try {

                    byte[] radio_frame_s = new byte[]{(char)0x0,(char)0x0,(char)0x0,(char)0x0,(char)0x0,(char)0x0,(char)0x0,(char)0x0,(char)0x0,(char)0x0};
/*
//-----------------------------структура передаваемых данных (взято с МК)
                    typedef struct radio_frame_s
                    {
                        uint16_t stx;//стартовое слово 0xa544
                        uint8_t crc; //контрольная сумма всего сообщения с солью в зависимости от msgid
                        uint8_t len;//длина поля данных
                        uint8_t seq;//счетчик пакетов
                        uint8_t sysid;// ид отправителя
                        uint8_t msgid;//тип сообщения
                        uint8_t data[];//данные максимум 50 байт если hc12
                    }radio_frame;
//------------------------------структура передаваемого сообщения (msgid 1)*/

                    int bytes = bis.read(radio_frame_s);
                    buffer.append((char)bytes);
                    Log.d(TAG, "read:" +bytes);
                    Log.d(TAG, "read to radio_frame_s: "+ radio_frame_s);
                    Log.d(TAG, "buffer.length: "+buffer.capacity());

                        sbConsole.append(buffer.toString());
                        buffer.delete(0, buffer.length());

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                etConsole.setText(sbConsole.toString());
                                etConsole.setMovementMethod(movementMethod);
                                etChars.setText(R.string.hello);
                            }
                        });

                }catch (IOException e){
                    e.printStackTrace();
                }
            }
            try {

                bis.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        public void write(byte[] b, int off, int len) {

            if (outputStream!=null) {

                try {
                    outputStream.write(b,off,len);

                    outputStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }



        public void cancel(){
            try {
                isConnected=false;
                //bluetoothSocket.close();
                inputStream.close();
                outputStream.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }

    }


    private void enableLed(byte[] kk, int i, int i1) {

//test t = new test();
//t.a = ByteBuffer.wrap(kk).getInt(0);
        if (connectedThread != null && connectThread.isConnect()) {
            connectedThread.write(kk, i, i1);

         /*
            connectedThread.write(byte[])*/
        }

    }

}


