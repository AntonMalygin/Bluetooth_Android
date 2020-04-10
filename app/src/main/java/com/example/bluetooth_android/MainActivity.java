package com.example.bluetooth_android;
import android.annotation.SuppressLint;
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
import android.os.Handler;
import android.os.Message;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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


public class MainActivity extends AppCompatActivity implements


        CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemClickListener,
        View.OnClickListener {

    private static final String TAG = "MY_APP_DEBUG_TAG";
    public static final int REQUEST_CODE_LOC = 1;

    private static final int REQ_ENABLE_BT = 10;
    public static final int BT_BOUNDED = 21;
    public static final int BT_SEARCH = 22;
    public static final int LED_RED = 30;
    public static final int LED_GREEN = 31;
    private static final int FRAME_OK =32;

    private StringBuilder sb = new StringBuilder();
  //  private final byte[] pcBlock = new byte[0];
  //  private final int len = 0;


    //------------------------------структура передаваемого сообщения (msgid 1) radio_data1_s
    class radio_data1_s {

        short dt_format; // формат календаря 10 или 16
        short dt_error;  // код ошибки часов
        @SuppressLint("SimpleDateFormat")
        DateFormat ds1307 = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");//часы, календарь (7 байт)
        short bm_error; //код ошибки bmp280
        float int_temp;//внутренняя температура град (bmp280)
        float press; //атмосферное давление мм рт ст (bmp280)
        short ds_error; //код ошибки ds18b20
        float ext_temp;//внешняя температура град (das18b20)


    }

    class radio_cmd_s
            //--------------------------структура команды (msgid 5) radio_cmd_s
    {
        short target_id; // идентификатор получателя команды
        short cmd;       // команда
        short len;       // число доп байт в команде
        short[] dat=new short[17];   // данные
    }

    class radio_cmd_resp_s
            //-----------------------ответ на команду (msgid 6) radio_cmd_resp_s
            // команда
            // результат операции
            // дополнительные данные
    {
        short cmd;      // команда
        short res;    // результат операции
        short[] dat = new short[17];    // дополнительные данные
    }

    class radio_frame_s {
             //-----------------------------структура передаваемых данных radio_frame_s
             short stx;//стартовое слово 0xa544
             short crc; //контрольная сумма всего сообщения с солью в зависимости от msgid
             short len;//длина поля данных
             short seq;//счетчик пакетов
             short sysid;// ид отправителя
             short msgid;//тип сообщения
             short[] data = new short[50];//данные максимум 50 байт если hc12

    }

    radio_frame_s radio_frame = new radio_frame_s();
    radio_frame_s radio_frame_received = new radio_frame_s();
    radio_cmd_resp_s radio_cmd_resp_s = new radio_cmd_resp_s();
    radio_cmd_s radio_cmd_s = new radio_cmd_s();
    radio_data1_s  radio_data1 = new radio_data1_s();

    private FrameLayout frameMessage;
    private LinearLayout frameControls;


    private RelativeLayout frameLedControls;
    private Button btnDisconnect;
    private Switch switchRedLed;
    private Switch switchGreenLed;
 //   private EditText etConsole;

    private TextView Dig_Frame_crc;
    private TextView Dig_Frame_len;
    private TextView Dig_Frame_seq;
    private TextView Dig_Frame_sysid;
    private TextView Dig_Frame_msgid;

    private TextView Dig_Temp_in;
    private TextView Dig_Temp_out;
    private TextView Dig_Press;

    //   private TextView Dig_Frame_data;
 //   private TextView Frame_data;

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






    //это массив соли, в зависимости от magic из массива берётся определённая цифра
    //Соль для затруднения определения алгоритма контрольной суммы
    byte[] extra_tab = new byte[]{
            (byte) ((char) 10), (byte) ((char) 12), (byte) ((char) 15), (byte) ((char) 18),
            (byte) ((char) 33), (byte) ((char) 134), (byte) ((char) 65), (byte) ((char) 234),
            (byte) ((char) 98), (byte) ((char) 68), (byte) ((char) 45), (byte) ((char) 234),
            (byte) ((char) 54), (byte) ((char) 57), (byte) ((char) 21), (byte) ((char) 61),
            (byte) ((char) 201), (byte) ((char) 69), (byte) ((char) 5), (byte) ((char) 241),
            (byte) ((char) 168), (byte) ((char) 23), (byte) ((char) 79), (byte) ((char) 62),
            (byte) ((char) 77)};
    char[] extra_tab_char = new char[]{128, 0x00, 127, 0x00};
    private Object crc8;




    @SuppressLint("HandlerLeak")
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
       // etConsole = findViewById(R.id.et_console);

        Dig_Frame_crc = findViewById(id.Dig_Frame_crc);// для вывода текста, полученного c Bluetooth
        Dig_Frame_len = findViewById(id.Dig_Frame_len);// для вывода текста, полученного c Bluetooth
        Dig_Frame_seq = findViewById(id.Dig_Frame_seq);// для вывода текста, полученного c Bluetooth
        Dig_Frame_sysid = findViewById(id.Dig_Frame_sysid);// для вывода текста, полученного c Bluetooth
        Dig_Frame_msgid = findViewById(id.Dig_Frame_msgid);// для вывода текста, полученного c Bluetooth

        Dig_Temp_in = findViewById(id.Dig_Temp_in);
        Dig_Temp_out = findViewById(id.Dig_Temp_out);
        Dig_Press = findViewById(id.Dig_Press);



        switchEnableBt.setOnCheckedChangeListener(this);
        btnEnableSearch.setOnClickListener(this);
        listBtDevices.setOnItemClickListener(this);

        btnDisconnect.setOnClickListener(this);
        switchGreenLed.setOnCheckedChangeListener(this);
        switchRedLed.setOnCheckedChangeListener(this);

        bluetoothDevices = new ArrayList<>();

        //Инициализация диалогового окна при подключении к устройству
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


    /*
     * Метод жизненного цикла. Вызывается тогда когда приложение закрывается.
     * В методе освобождаются ресурсы передатчика Блютус и два метода connectedThread/connectThread
     * */

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver); // Регистрация receiver в методе onDestroy
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
            // Отображения списка сопряженных устройств
            showFrameControls();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Обработка нажатия на элемент списка
        if (parent.equals(listBtDevices)) {
            BluetoothDevice device = bluetoothDevices.get(position);  // Вытаскиваем Устройство блютус с массива устройств через позицию
            if (device != null) {
                connectThread = new ConnectThread(device);
                connectThread.start(); // Попытка соединиться с устройством на которое нажали
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
            Log.d(TAG, "switchRedLed:  Переключаем красный");
            enableLed(extra_tab, 0, 4);

        } else if (buttonView.equals(switchGreenLed)) {
            // TODO включение или отключение зелёного светодиода
             enableLed(extra_tab,0x5, 0x5);
            Log.d(TAG, "switchGreenLed:  Переключаем зелёный");

            extra_tab[3] = (byte) 0x66;
            extra_tab[2] = (byte) 0xE6;
            extra_tab[1] = (byte) 0xF6;
            extra_tab[0] = (byte) 0x42;
            //  t.a_float = Float.parseFloat("19.95");




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
    //Подпрограмма преобразования формата char в формат byte (пока до конца не протестирована)
    public byte[] charsToBytes(char[] chars) {
        Charset charset = StandardCharsets.UTF_8;
        ByteBuffer byteBuffer = charset.encode(CharBuffer.wrap(chars));
        return Arrays.copyOf(byteBuffer.array(), byteBuffer.limit());
    }
    //Подпрограмма преобразования формата byte в формат char (пока до конца не протестирована)
    public char[] bytesToChars(byte[] bytes) {
        Charset charset = StandardCharsets.UTF_8;
        CharBuffer charBuffer = charset.decode(ByteBuffer.wrap(bytes));
        return Arrays.copyOf(charBuffer.array(), charBuffer.limit());
    }

    // Программа преобразования формата short формат флоат
    public float shortToFloat (short[] data, short ukz){

        float value = Float.intBitsToFloat(data[ukz] ^ data[ukz + 1] << 8 ^ data[ukz + 2] << 16 ^ data[ukz + 3] << 24);

        return value;
    }


    // расчёт контрольной суммы 8 бит возвращает контрольную сумму
    /*
  Name  : CRC-8
  Poly  : 0x31    x^8 + x^5 + x^4 + 1
  Init  : 0xFF
  Revert: false
  XorOut: 0x00
  Check : 0xF7 ("123456789")
  MaxLen: 15 байт(127 бит) - обнаружение
    одинарных, двойных, тройных и всех нечетных ошибок
*/
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
//метод подготовки данных для передачи на часы. Пока не переписана на JAVA
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
    @SuppressLint("HandlerLeak")
    private Handler outHandler =new Handler(){

        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){

                case FRAME_OK:
                   Dig_Frame_crc.setText(Short.toString(radio_frame_received.crc));
                   Dig_Frame_len.setText(Short.toString(radio_frame_received.len));
                   Dig_Frame_seq.setText(Short.toString(radio_frame_received.seq));
                   Dig_Frame_sysid.setText(Short.toString(radio_frame_received.sysid));
                   Dig_Frame_msgid.setText(Short.toString(radio_frame_received.msgid));
                    Dig_Temp_in.setText(Float.toString(radio_data1.int_temp));
                    Dig_Temp_out.setText(Float.toString(radio_data1.ext_temp));
                    Dig_Press.setText(Float.toString(radio_data1.press));
                  //  radio_data1.int_temp=shortToFloat(radio_frame_received.data, (short) 10);
                   // Toast.makeText(MainActivity.this, "FRAME_OK " + Integer.toHexString(radio_frame_received.crc), Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "handleMessage: Frame OK" + radio_data1.int_temp);


            }

        }
    };


    private class ConnectThread extends Thread {


        private BluetoothSocket bluetoothSocket = null;
        private boolean success = false;


        public ConnectThread(BluetoothDevice device) {
            try {
                Method method = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                bluetoothSocket = (BluetoothSocket) method.invoke(device, 1);
                progressDialog.show(); // отображение диалогового окна о текущем соединении с устройством
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                bluetoothSocket.connect();
                success = true;
                progressDialog.dismiss(); // Скрывание диалогового окна о текущем соединении с устройством при удачном
            } catch (IOException e) {
                e.printStackTrace();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss(); // Скрывание диалогового окна о текущем соединении с устройством при удачном
                        Toast.makeText(MainActivity.this, getString(dont_connect), Toast.LENGTH_SHORT).show();
                    }
                });

                cancel();
            }

            if (success) {
                connectedThread = new ConnectedThread(bluetoothSocket, outHandler); // создаём экземпляр класса ConnectedThread и передаём ему блютуссокет
                connectedThread.start();// запуск
                // для вмешательства стороннего потока в пользовательский интерфейс создается метод
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showFrameLedControls();// если соединение с устройством прошло удачно, то отображаем панельку управления светодиодами
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
    // Класс считывания информации с устройства
    private class ConnectedThread extends Thread {


        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; //store for the stream
        private boolean isConnected = false;// Состояние соединения (Активно/Неактивно)
        private Handler handler;

        public ConnectedThread(BluetoothSocket socket, Handler handler){
            mmSocket=socket;
            this.handler = handler;
            InputStream tmpIn   = null;
            OutputStream tmpOut = null;

            try{
                tmpIn=socket.getInputStream();

            } catch (IOException e){
                e.printStackTrace();
                Log.e(TAG, "Error occurred when creating input stream", e);
            }

            try{

                tmpOut=socket.getOutputStream();
            } catch (IOException e){
                e.printStackTrace();
                Log.e(TAG, "Error occurred when creating output stream", e);
            }
            this.mmInStream=tmpIn;
            this.mmOutStream=tmpOut;

            isConnected = true;
        }
        // Вызов из Майн Активити и отправка данных в у даленное устройство

        @Override
        public void run(){

            mmBuffer = new byte[60];
            int numBytes; // Количество байт принятых из read()
            final short [] mmB = new short[60];
            short len, temp;
            len=0;
            temp=0;
            short ukz,i;
            ukz=0;
            i=0;


            while (isConnected){
                try {


                    numBytes = mmInStream.read(mmBuffer);


                    for (i=0;i<numBytes;i++)
                    {
//
                        mmB[ukz]= (short) (mmBuffer[i]&0xFF); // Пишем данные во временный массив, и делаем Логическое И, что бы числа не были больше 0xFF (255)





                        if ((ukz == 0)&&(mmB[0]==0xA5))     // Принимаем первый байт заголовка
                        {

                            ukz++;
                            continue;
                        }
                        if (ukz==1){                            // Принимаем второй байт заголовка
                            if (mmB[1]==0x44){
                                ukz++;
                            } else {ukz=0;}
                            continue;
                        }
                        if (ukz<2)
                        {
                          continue;  // Если указатель меньше 2 то переходим на начала цикла for
                        }
                         ukz++;                          // сдвигаем указатель на позицию
                        if (ukz>=7)
                        {
                            if ((mmB[3]+7)==ukz)
                            {
                                ukz=0;
                                len = mmB[3];
                                radio_frame_received.crc=mmB[2]; // Контрольная сумма
                                radio_frame_received.len=mmB[3];// Длина поля данных
                                radio_frame_received.seq=mmB[4];// счетчик пакетов
                                radio_frame_received.sysid=mmB[5];// ид отправителя
                                radio_frame_received.msgid=mmB[6];// тип сообщения
                                System.arraycopy(mmB,7, radio_frame_received.data,0,len);
                                radio_data1.int_temp=shortToFloat(radio_frame_received.data, (short) 10);
                                radio_data1.ext_temp=shortToFloat(radio_frame_received.data, (short) 19);
                                radio_data1.press=shortToFloat(radio_frame_received.data, (short) 14);
                                handler.sendEmptyMessage(FRAME_OK);
                            }
                        }
                    }


                    // Наполнение экземпляра массива radio_frame_s данными полученными с блютус
              //      radio_frame_received.crc=mmB[2]; // Контрольная сумма
              //      radio_frame_received.len=mmB[3];// Длина поля данных
              //      radio_frame_received.seq=mmB[4];// счетчик пакетов
              //      radio_frame_received.sysid=mmB[5];// ид отправителя
              //      radio_frame_received.msgid=mmB[6];// тип сообщения



                }catch (IOException e){
                    e.printStackTrace();
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;

                }
            }
            try {

                mmSocket.close();
            }catch (IOException e){
                e.printStackTrace();
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }

        public void write(byte[] b, int off, int len) {

            if (mmOutStream!=null) {

                try {
                    mmOutStream.write(b,off,len);

                    mmOutStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error occurred when sending data", e);
                }
            }
        }



        public void cancel(){
            try {
                isConnected=false;
                //bluetoothSocket.close();
                mmInStream.close();
                mmOutStream.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }

    }


    private void enableLed(byte[] kk, int i, int i1) {


        if (connectedThread != null && connectThread.isConnect()) {
            connectedThread.write(kk, i, i1);

         /*
            connectedThread.write(byte[])*/
        }

    }

}


