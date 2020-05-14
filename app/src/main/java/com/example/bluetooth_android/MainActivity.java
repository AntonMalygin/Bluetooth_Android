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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import es.dmoral.toasty.Toasty;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static com.example.bluetooth_android.R.drawable;
import static com.example.bluetooth_android.R.id;
import static com.example.bluetooth_android.R.layout;
import static com.example.bluetooth_android.R.string.Connecting;
import static com.example.bluetooth_android.R.string.Temp_in;
import static com.example.bluetooth_android.R.string.bluetooth_not_supported;
import static com.example.bluetooth_android.R.string.dont_connect;
import static com.example.bluetooth_android.R.string.please_wait;
import static com.example.bluetooth_android.R.string.start_search;
import static com.example.bluetooth_android.R.string.stop_search;


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
    private static final int SEND_ERROR = 33;

    // Присвоение констант для часов (команды и т.п.)
    private static final byte SYNX_CLOCK = 2;
    private static final byte ID_SYS_Clock = 4;
    private static final int SYNX_CLOCK_ERROR = 5; // Ошибка синхронизирования часов
    private static byte sh_seq=0;//вставляем счетчик пакетов


    private boolean flag_clock_synx=false; // флаг для синхронизации часов с текущим временем телефона


    //------------------------------структура передаваемого сообщения (msgid 1) radio_data1_s
    static class radio_data1_s {

        byte dt_format; // формат календаря 10 или 16
        byte dt_error;  // код ошибки часов

        String str_data = "";
        String str_time = "";
        byte bm_error; //код ошибки bmp280
        float int_temp;//внутренняя температура град (bmp280)
        float press; //атмосферное давление мм рт ст (bmp280)
        byte ds_error; //код ошибки ds18b20
        float ext_temp;//внешняя температура град (ds18b20)


    }

    static class radio_cmd_s
            //--------------------------структура команды (msgid 5) radio_cmd_s
    {
        byte target_id; // идентификатор получателя команды
        byte cmd;       // команда
        byte len;       // число доп байт в команде
        byte[] dat=new byte[17];   // данные
    }

    static class radio_cmd_resp_s
            //-----------------------ответ на команду (msgid 6) radio_cmd_resp_s
            // команда
            // результат операции
            // дополнительные данные
    {
        byte cmd;      // команда
        byte res;    // результат операции
        byte[] dat = new byte[17];    // дополнительные данные
    }

    static class radio_frame_s {
             //-----------------------------структура передаваемых данных radio_frame_s
             byte stx_1;//стартовое слово 0xA5
             byte stx_2;//стартовое слово 0x44
             byte crc; //контрольная сумма всего сообщения с солью в зависимости от msgid
             byte len;//длина поля данных
             byte seq;//счетчик пакетов
             byte sysid;// ид отправителя
             byte msgid;//тип сообщения
             byte[] data = new byte[50];//данные максимум 50 байт если hc12

    }

    radio_frame_s radio_frame_send = new radio_frame_s();
    radio_frame_s radio_frame_received = new radio_frame_s();
    radio_cmd_resp_s radio_cmd_resp_s = new radio_cmd_resp_s();
    radio_cmd_s radio_cmd_s = new radio_cmd_s();
    radio_data1_s  radio_data1 = new radio_data1_s();

    private FrameLayout frameMessage;
    private LinearLayout frameControls;


    private RelativeLayout frameLedControls;
    private Button btnDisconnect;
    private Button clock_Synx;



    private TextView Temp_in;
    private TextView Temp_out;
    private TextView Pressure;



    private TextView Dig_Temp_in;
    private TextView Dig_Temp_out;
    private TextView Dig_Press;

    private TextView Systimes;
    private TextView Dig_Time;
    private TextView Dig_Date;

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

private ColorStateList  oldColors;



    //это массив соли, в зависимости от magic из массива берётся определённая цифра
    //Соль для затруднения определения алгоритма контрольной суммы
    byte[] extra_tab = new byte[]{10,12,15,18,33, (byte) 134, 65, (byte)234,98,68,45,
            (byte)234,54, 57,21,61,(byte) 201,69,5, (byte)241,(byte)168,23,79,62,77};






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

        Temp_in = findViewById(id.Temp_in);
        Temp_out = findViewById(id.Temp_out);
        Pressure = findViewById(id.Pressure);

        oldColors =  Temp_in.getTextColors(); //save original colors

        Dig_Temp_in = findViewById(id.Dig_Temp_in);
        Dig_Temp_out = findViewById(id.Dig_Temp_out);
        Dig_Press = findViewById(id.Dig_Press);

        TextView Time = findViewById(id.Time);
        TextView Date = findViewById(id.Date);
        clock_Synx = findViewById(id.Clock_sinx);

        Systimes = findViewById(id.sys_time);
        Dig_Date = findViewById(id.Dig_Date);
        Dig_Time = findViewById(id.Dig_Time);

        switchEnableBt.setOnCheckedChangeListener(this);
        btnEnableSearch.setOnClickListener(this);
        listBtDevices.setOnItemClickListener(this);

        btnDisconnect.setOnClickListener(this);
        clock_Synx.setOnClickListener(this);



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

            Toasty.info(MainActivity.this, bluetooth_not_supported,Toast.LENGTH_SHORT).show();

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
        } else if (v.equals(clock_Synx)){
            flag_clock_synx=true;


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

    // Программа преобразования формата byte в формат float
    public static float byteToFloat (byte[] data, byte ukz)
    {
        float float_temp;
        float_temp=Float.intBitsToFloat(data[ukz+3]<<24 | (data[ukz+2]& 0xFF) << 16 | (data[ukz+1]& 0xFF) << 8 | (data[ukz]& 0xFF));
        return float_temp;

    }

    // Программа преобразования формата float в формат byte
    public static byte[] FloatToByteArray (float value)
    {
        int intBits =  Float.floatToIntBits(value);
        return new byte[] {
                (byte) (intBits >> 24), (byte) (intBits >> 16), (byte) (intBits >> 8), (byte) (intBits) };
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
    private byte crc8(byte[] pcBook, byte len) {
        byte i, i1;
        i=0;
        i1=0;

        byte crc = (byte)0xFF;

        for (i1 = 0; i1 < len; i1++) {

            crc ^= pcBook[i1];

            for (i = 0; i < 8; i++) {
                if ((crc & (byte) 0x80) == (byte) 0x80) crc =  (byte)(((crc << 1) ^ 0x31));
                else crc =  (byte)(crc << 1);
            }

        }
        return crc;
    }
//метод подготовки данных для передачи на часы. Пока не переписана на JAVA
 //   private void radio_pool() {
//
  //  }




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



    @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
    @SuppressLint("SimpleDateFormat") SimpleDateFormat hour = new SimpleDateFormat("HH");
    @SuppressLint("SimpleDateFormat") SimpleDateFormat mm = new SimpleDateFormat("mm");
    @SuppressLint("SimpleDateFormat") SimpleDateFormat ss = new SimpleDateFormat("ss");
    @SuppressLint("SimpleDateFormat") SimpleDateFormat dd = new SimpleDateFormat("dd");
    @SuppressLint("SimpleDateFormat") SimpleDateFormat MM = new SimpleDateFormat("MM");
    @SuppressLint("SimpleDateFormat") SimpleDateFormat yy = new SimpleDateFormat("yy");



    @SuppressLint("HandlerLeak")
    private final Handler outHandler = new Handler() {

        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(Message msg) {



            if (msg.what ==FRAME_OK) {

                if (radio_data1.bm_error==0){
                    Dig_Temp_in.setTextColor(oldColors);
                    Dig_Press.setTextColor(oldColors);
                    Dig_Temp_in.setText(Float.toString(radio_data1.int_temp));
                    Dig_Press.setText(Float.toString(radio_data1.press));
                }else {
                    Dig_Temp_in.setTextColor(Color.RED);
                    Dig_Press.setTextColor(Color.RED);
                    Dig_Temp_in.setText("err "+radio_data1.bm_error);
                    Dig_Press.setText("err "+radio_data1.bm_error);
                }

                if (radio_data1.ds_error==0){
                    Dig_Temp_out.setTextColor(oldColors);
                    Dig_Temp_out.setText(Float.toString(radio_data1.ext_temp));
                }else {
                    Dig_Temp_out.setTextColor(Color.RED);
                    Dig_Temp_out.setText("err "+radio_data1.ds_error);
                }





                Dig_Time.setText(radio_data1.str_time);
                Dig_Date.setText(radio_data1.str_data);
                Systimes.setText(sdf.format(new Date(System.currentTimeMillis())));
            }

            if (msg.what == SEND_ERROR){

                Toasty.error(MainActivity.this, getString(R.string.Send_error), Toasty.LENGTH_SHORT, true).show();
            }

            if (msg.what == SYNX_CLOCK){

                Toasty.info(MainActivity.this,R.string.Synx_clock, Toast.LENGTH_SHORT).show();
            }

            if (msg.what == SYNX_CLOCK_ERROR){

                Toasty.error(MainActivity.this, getString(R.string.notSynx_clock)+ " " +getString(R.string.error_code) +" "+ radio_data1.ds_error, Toasty.LENGTH_LONG, true).show();
            }

        }
    };


    private class ConnectThread extends Thread {


        private BluetoothSocket bluetoothSocket = null;
        private boolean success = false;


        ConnectThread(BluetoothDevice device) {
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

                        Toasty.error(MainActivity.this, dont_connect,Toast.LENGTH_SHORT).show();
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

        boolean isConnect() {
            return bluetoothSocket.isConnected();
        }


        void cancel() {
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
        private boolean isConnected = false;// Состояние соединения (Активно/Неактивно)
        private Handler handler;

        ConnectedThread(BluetoothSocket socket, Handler handler){
            mmSocket=socket;
            this.handler = handler;
            InputStream tmpIn   = null;
            OutputStream tmpOut = null;

            try{
                tmpIn=socket.getInputStream();

            } catch (IOException e){
                e.printStackTrace();

            }

            try{

                tmpOut=socket.getOutputStream();
            } catch (IOException e){
                e.printStackTrace();

            }
            this.mmInStream=tmpIn;
            this.mmOutStream=tmpOut;

            isConnected = true;
        }
        // Вызов из Майн Активити и отправка данных в у даленное устройство

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void run(){

            //store for the stream
            byte[] mmBuffer = new byte[60];
            int numBytes; // Количество байт принятых из read()
            final byte [] mmB = new byte[60];
            short len;
            len=0;

            byte ukz,i, crc_in, crc_temp;
            ukz=0;


            while (isConnected){
                try {


                    numBytes = mmInStream.read(mmBuffer);


                    for (i=0;i<numBytes;i++)
                    {
//
                        mmB[ukz]= mmBuffer[i]; // Пишем данные во временный массив, и делаем Логическое И, что бы числа не были больше 0xFF (255)


                        // Принимаем первый байт заголовка
                        if (ukz == 0)
                            if (mmB[0] ==(byte)0xA5) {

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
                                crc_temp=mmB[2];
                                mmB[2]=extra_tab[mmB[6]];
                                crc_in=crc8(mmB,ukz);


                                ukz=0;
                                if (crc_temp==crc_in){
                                // Если контрольная сумма пакета сошлась то можно отправлять данные дальше по алгоритму
                                    len = mmB[3];
                                    radio_frame_received.crc=mmB[2]; // Контрольная сумма
                                    radio_frame_received.len=mmB[3];// Длина поля данных
                                    radio_frame_received.seq=mmB[4];// счетчик пакетов
                                    radio_frame_received.sysid=mmB[5];// ид отправителя
                                    radio_frame_received.msgid=mmB[6];// тип сообщения
                                    System.arraycopy(mmB,7, radio_frame_received.data,0,len);


                                }

                                if (radio_frame_received.msgid==1){
                                    // Заполняем структуру стандартной передачи данных
                                    radio_data1.dt_format=radio_frame_received.data[0];                     // Формат календаря 10 или 16
                                    radio_data1.dt_error=radio_frame_received.data[1];                      // Код ошибки часов
                                    radio_data1.bm_error=radio_frame_received.data[9];                      // Код ошибки bmp280

                                    radio_data1.int_temp=byteToFloat(radio_frame_received.data,(byte)10);
                                    radio_data1.press=byteToFloat(radio_frame_received.data, (byte) 14);

                                    radio_data1.ds_error=radio_frame_received.data[18];                     // Код ошибки ds18b20
                                    radio_data1.ext_temp=byteToFloat(radio_frame_received.data, (byte) 19);


                                    // Подготовка строки с форматирование для вывода на экран времени часов
                                    radio_data1.str_time=String.format("%02x",radio_frame_received.data[4])+":"+String.format("%02x",radio_frame_received.data[3])+":"+String.format("%02x",radio_frame_received.data[2]);

                                    // Подготовка строки с форматирование для вывода на экран даты часов
                                    radio_data1.str_data=String.format("%02x",radio_frame_received.data[6])+"."+String.format("%02x",radio_frame_received.data[7])+"."+String.format("%02x",radio_frame_received.data[8]);
                                    handler.sendEmptyMessage(FRAME_OK);
                                }


                                if (radio_frame_received.msgid==6){
                                    // Заполняем структуру ответа на команду
                                    radio_cmd_resp_s.cmd=radio_frame_received.data[0];
                                    radio_cmd_resp_s.res=radio_frame_received.data[1];

                                    System.arraycopy(radio_frame_received.data,2, radio_cmd_resp_s.dat,0,radio_frame_received.len-2); // перегоняем из приёмного массива в массив ответа на команду
                                    if (radio_cmd_resp_s.cmd==2){
                                        if(radio_cmd_resp_s.res==0){
                                            handler.sendEmptyMessage(SYNX_CLOCK);
                                        }
                                        if(radio_cmd_resp_s.res!=0){
                                            radio_data1.ds_error=radio_cmd_resp_s.res;
                                            handler.sendEmptyMessage(SYNX_CLOCK_ERROR);
                                        }


                                    }

                                }

                            }
                        }
                    }


                    if (flag_clock_synx){
                        radio_cmd_s.target_id=ID_SYS_Clock;
                        radio_cmd_s.cmd=2;

                        String S_hour =hour.format(new Date(System.currentTimeMillis()));
                        String S_min = mm.format(new Date(System.currentTimeMillis()));
                        String S_sec = ss.format(new Date(System.currentTimeMillis()));
                        String S_day = dd.format(new Date(System.currentTimeMillis()));
                        String S_mounth = MM.format(new Date(System.currentTimeMillis()));
                        String S_year = yy.format(new Date(System.currentTimeMillis()));


                        int hour_t= Integer.parseInt(S_hour, 16);
                        int min_t= Integer.parseInt(S_min, 16);
                        int sec_t= Integer.parseInt(S_sec, 16);
                        int day_t= Integer.parseInt(S_day, 16);
                        int mounth_t= Integer.parseInt(S_mounth, 16);
                        int year_t= Integer.parseInt(S_year, 16);


                        radio_cmd_s.dat[0] = (byte)sec_t;
                        radio_cmd_s.dat[1]  = (byte)min_t;
                        radio_cmd_s.dat[2]  = (byte)hour_t;

                        radio_cmd_s.dat[4]  = (byte)day_t;
                        radio_cmd_s.dat[5]  = (byte)mounth_t;
                        radio_cmd_s.dat[6]  = (byte)year_t;
                        radio_cmd_s.len= (byte) 7;

                        send_msg(radio_cmd_s.target_id,radio_cmd_s.cmd,radio_cmd_s.len,radio_cmd_s.dat);
                        flag_clock_synx = false;

                    }


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

        public void write(byte[] bytes) {

            if (mmOutStream!=null) {

                try {
                    mmOutStream.write(bytes);
                    // b     the data.
                    // off   the start offset in the data.
                    // len   the number of bytes to write.

                    mmOutStream.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Error occurred when sending data", e);
                    handler.sendEmptyMessage(SEND_ERROR);
                }
            }
        }



        void cancel(){
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


    private void send_msg(byte target_id, byte cmd, byte len, byte[] dat) {

int i,i1;

i=0;
i1=0;



//Сборка пакета на передачу
    radio_frame_send.stx_1= (byte) 0xA5;
    radio_frame_send.stx_2=  (byte) 0x44;
    radio_frame_send.crc=extra_tab[5];
    radio_frame_send.len= (byte) (len+4);
    radio_frame_send.seq=sh_seq;
    radio_frame_send.sysid=target_id;
    radio_frame_send.msgid=(byte) 0x5;
    System.arraycopy(dat,0,radio_frame_send.data,4,len+4);
    radio_frame_send.data[0]=target_id;
    radio_frame_send.data[1]=cmd;
    radio_frame_send.data[2]= (byte) (len+1);
    radio_frame_send.data[3]=16;

        if (sh_seq>=0){
            sh_seq++;
        }else {
            sh_seq=0;
        }



        byte[] kk = new byte[radio_frame_send.len+7];

        kk[0]=radio_frame_send.stx_1;
        kk[1]=radio_frame_send.stx_2;
        kk[2]=radio_frame_send.crc;
        kk[3]=radio_frame_send.len;
        kk[4]=radio_frame_send.seq;
        kk[5]=radio_frame_send.sysid;
        kk[6]=radio_frame_send.msgid;
        System.arraycopy(radio_frame_send.data,0,kk,7,len+4);

        // Расчёт контрольной суммы пакета
                radio_frame_send.crc=crc8(kk, (byte)(radio_frame_send.len+7));
        kk[2]=radio_frame_send.crc;

        //        // Расчёт контрольной суммы пакета

        if (connectedThread != null && connectThread.isConnect()) {
            connectedThread.write(kk);


        }

    }

}


