package de.heimbeeren.homecycling;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.TransitionDrawable;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.w3c.dom.Text;

import java.util.List;

public class WorkoutActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener,View.OnClickListener {

    // Constant for Logging-Tag
    private static final String TAG = WorkoutActivity.class.getSimpleName();

    // Constants for transferring BLE-Device Attributes between Activities
    private static final int REQUEST_ENABLE_BT = 1;
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    // Have the BLE Service referenced
    BluetoothLeService mBluetoothLeService;

    // Helper-Flag for Device Connection State
    boolean mDeviceConnected;

    // Flag showing wether User is logged in or not.
    // private boolean userLoggedIn = false;
    private boolean userLoggedIn = true; // for Debugging

    // Variables for Views
    TextView txvMessage, txvHeartRate, txvHeartRatePct, txvHRStage1, txvHRStage2, txvHRStage3,
            txvHRStage4, txvHRStage5, txvDeviceName;
    ViewGroup heartrateArea;

    // Variables for our Animations
    TransitionDrawable stage1Transition, stage2Transition, stage3Transition, stage4Transition,
            stage5Transition;

    // Variable for Seekbar, needed for debugging purposes
    SeekBar debugSeeker;

    // HeartRate related Variables

    int currentHR, currentRange, minRekomHR, minGA1HR, minGA2HR, minEBHR, minSBHR;
    float maxHeartRate = 180;

    // Ranges
    public static final int RESTING_RANGE = 0;
    public static final int REKOM_RANGE = 1;
    public static final int GA1_RANGE = 2;
    public static final int GA2_RANGE = 3;
    public static final int EB_RANGE = 4;
    public static final int SB_RANGE = 5;
    String beltDeviceName;
    String beltDeviceAddress;
    BluetoothAdapter mBluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_workout);

        // We don't want to let the lights go off.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        currentRange = RESTING_RANGE;

        // First of all, have bluetooth switched on if it isn't
        initializeBluetooth();


        // Go, get the Views
        initializeViews();

        loadPreferences();

        // Let's get the Bluetooth LE Service
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Show warning if no user is logged in.
        if (userLoggedIn == false) {
            txvMessage.setText(R.string.text_no_user_logged_in);
        } else {
            // Calculate the personal Heartrate ranges for the workout
            calculateHRRanges();
        }
    }

    // Actions to perform on messages from the heartrate belt
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mDeviceConnected = true;
                txvDeviceName.setTextColor(Color.GREEN);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mDeviceConnected = false;
                txvDeviceName.setTextColor(Color.RED);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                refreshHRRange(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };


    // Method for getting references to the views
    private void initializeViews() {
        txvMessage = (TextView) findViewById(R.id.message_view);
        debugSeeker = (SeekBar) findViewById(R.id.debug_seeker);
        heartrateArea = (ViewGroup) findViewById(R.id.heartrate_area);
        txvHeartRate = (TextView) findViewById(R.id.textview_heartrate);
        txvHeartRatePct = (TextView) findViewById(R.id.txv_heartrate_pct);
        txvDeviceName = (TextView) findViewById(R.id.txv_device_name);
        txvHRStage1 = (TextView) findViewById(R.id.txv_hr_stage1);
        txvHRStage2 = (TextView) findViewById(R.id.txv_hr_stage2);
        txvHRStage3 = (TextView) findViewById(R.id.txv_hr_stage3);
        txvHRStage4 = (TextView) findViewById(R.id.txv_hr_stage4);
        txvHRStage5 = (TextView) findViewById(R.id.txv_hr_stage5);
        stage1Transition = (TransitionDrawable) txvHRStage1.getBackground();
        stage2Transition = (TransitionDrawable) txvHRStage2.getBackground();
        stage3Transition = (TransitionDrawable) txvHRStage3.getBackground();
        stage4Transition = (TransitionDrawable) txvHRStage4.getBackground();
        stage5Transition = (TransitionDrawable) txvHRStage5.getBackground();
        debugSeeker.setOnSeekBarChangeListener(this);
        debugSeeker.setMax(Math.round(maxHeartRate));
        heartrateArea.setOnClickListener(this);
    }

    // Method to calculate the personal Heartrate Ranges
    private void calculateHRRanges() {
        minRekomHR = (int) Math.round(maxHeartRate / 2);
        minGA1HR = (int) Math.round(maxHeartRate * 0.6);
        minGA2HR = (int) Math.round(maxHeartRate * 0.7);
        minEBHR = (int) Math.round(maxHeartRate * 0.8);
        minSBHR = (int) Math.round(maxHeartRate * 0.9);
    }

    // This is to do when our debugging seekbar changes its value.
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        currentHR = progress;
        if (userLoggedIn) {
            refreshHRRange(Integer.toString(progress));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    // Refresh our screen display for the heartrate
    private void refreshHRRange(String heartRate) {
        currentHR = Integer.parseInt(heartRate);
        float pctmaxHR = (float) currentHR / maxHeartRate * 100;
        txvHeartRatePct.setText(Integer.toString(Math.round(pctmaxHR)) + "%");
        txvHeartRate.setText(Integer.toString(currentHR));
        int oldRange = currentRange;
        if (currentHR < minRekomHR) {
            currentRange = RESTING_RANGE;
        } else if (currentHR >= minRekomHR && currentHR < minGA1HR) {
            currentRange = REKOM_RANGE;
        } else if (currentHR >= minGA1HR && currentHR < minGA2HR) {
            currentRange = GA1_RANGE;
        } else if (currentHR >= minGA2HR && currentHR < minEBHR) {
            currentRange = GA2_RANGE;
        } else if (currentHR >= minEBHR && currentHR < minSBHR) {
            currentRange = EB_RANGE;
        } else if (currentHR >= minSBHR) {
            currentRange = SB_RANGE;
        }
        if (oldRange != currentRange) {
            Log.d(TAG, "We have to change the Display");
            stage1Transition.resetTransition();
            stage2Transition.resetTransition();
            stage3Transition.resetTransition();
            stage4Transition.resetTransition();
            stage5Transition.resetTransition();
            switch (currentRange) {
                case REKOM_RANGE:
                    Log.d(TAG, "Neuer Bereich: Regenerationsbereich");
                    stage1Transition.startTransition(1000);
                    stage2Transition.resetTransition();
                    stage3Transition.resetTransition();
                    stage4Transition.resetTransition();
                    stage5Transition.resetTransition();
                    break;
                case GA1_RANGE:
                    Log.d(TAG, "Neuer Bereich: GA1");
                    stage1Transition.startTransition(1000);
                    stage2Transition.startTransition(1000);
                    stage3Transition.resetTransition();
                    stage4Transition.resetTransition();
                    stage5Transition.resetTransition();
                    break;
                case GA2_RANGE:
                    Log.d(TAG, "Neuer Bereich: GA2");
                    stage1Transition.startTransition(1000);
                    stage2Transition.startTransition(1000);
                    stage3Transition.startTransition(1000);
                    stage4Transition.resetTransition();
                    stage5Transition.resetTransition();
                    break;
                case EB_RANGE:
                    Log.d(TAG, "Neuer Bereich: Entwicklungsbereich");
                    stage1Transition.startTransition(1000);
                    stage2Transition.startTransition(1000);
                    stage3Transition.startTransition(1000);
                    stage4Transition.startTransition(1000);
                    stage5Transition.resetTransition();
                    break;
                case SB_RANGE:
                    Log.d(TAG, "Neuer Bereich: Spitzenbereich");
                    stage1Transition.startTransition(1000);
                    stage2Transition.startTransition(1000);
                    stage3Transition.startTransition(1000);
                    stage4Transition.startTransition(1000);
                    stage5Transition.startTransition(1000);
                    break;
                default:
                    Log.d(TAG, "Neuer Bereich: Außerhalb Trainingsbereiche");
                    stage1Transition.resetTransition();
                    stage1Transition.resetTransition();
                    stage3Transition.resetTransition();
                    stage4Transition.resetTransition();
                    stage5Transition.resetTransition();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            // Brustgurt-Auswahl-Activity? Dann
            // Rückgabewerte auffangen.
            if (!data.getStringExtra(EXTRAS_DEVICE_NAME).equals("")) {
                beltDeviceName = data.getStringExtra(EXTRAS_DEVICE_NAME);
                beltDeviceAddress = data.getStringExtra(EXTRAS_DEVICE_ADDRESS);
                SharedPreferences pref = this.getSharedPreferences("PreferredHRBelt", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putString("beltname", beltDeviceName);
                editor.putString("beltaddress", beltDeviceAddress);
                editor.apply();
                // Textviews zum Anzeigen des ausgewählten Brustgurtes.
                txvDeviceName.setText(beltDeviceName);
            }
        }
        else if (requestCode == 2) {
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.heartrate_area) {
            if (mDeviceConnected) {
                mBluetoothLeService.disconnect();
            }
            Log.d(TAG, "Requested Device Scan Activity");
            // Auswahl-Activity für den Brustgurt starten. Als Rückgabe wird der Name
            // und die Seriennummer des Brustgurtes als String erwartet.
            Intent getHeartRateBeltIntent = new Intent(this, DeviceScanActivity.class);
            final int REQUEST_A_BELT = 1;
            startActivityForResult(getHeartRateBeltIntent, REQUEST_A_BELT);
        }
    }

    private void initializeBluetooth() {
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(beltDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }

    };

    // Activity Lifecycle stuff
    @Override
    protected void onResume() {
        super.onResume();
        // Wenn Activity wieder aufwacht, Verbindung zum Brustgurt wiederherstellen.
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(beltDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Activity pausiert --> Brustgurt pausiert.
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Jemand Activity kaputt gemacht? Brustgurt abklemmen.
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    // Bluetooth Geraffel
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();

            if (uuid.equals("0000180d-0000-1000-8000-00805f9b34fb")) {
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                }
            }
        }
    }

    private void loadPreferences() {
        SharedPreferences pref = this.getSharedPreferences("PreferredHRBelt", Context.MODE_PRIVATE);
        if (pref.contains("beltname")) {
            beltDeviceName = pref.getString("beltname", "");
            beltDeviceAddress = pref.getString("beltaddress", "");
            txvDeviceName.setText(beltDeviceName);
        }
    }
}
