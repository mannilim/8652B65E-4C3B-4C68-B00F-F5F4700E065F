package com.example.bluetoothprinting;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bluetoothprinting.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import wpprinter.printer.WpPrinter;

public class MainActivity extends AppCompatActivity {
    static WpPrinter printer;
    private ActivityMainBinding binding;

    private long connectingStartTime = System.currentTimeMillis();
    private long connectingFinishTime = System.currentTimeMillis();
    private String address;
    private boolean isClaiming = false;
    private boolean isClaimed = false;
    private boolean isReleasing = false;
    private boolean autoGenerateInvoice = false;
    private AlertDialog.Builder builder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.button1.setOnClickListener(view -> onButtonClicked(false));
        binding.button2.setOnClickListener(view -> onButtonClicked(true));
        int maxPrintingLength = 12;
        binding.seekBar.setMax(maxPrintingLength);
        int defaultPrintingLength = 3;
        binding.seekBar.setProgress(defaultPrintingLength);
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateUserInterface();
            }

            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        binding.button3.setOnClickListener(view -> quitApp());

        builder = new AlertDialog.Builder(this);

        printer = new WpPrinter(this, new Handler(Looper.getMainLooper(), message -> {
            if (message.what == WpPrinter.MESSAGE_BLUETOOTH_DEVICE_SET) {
                onDevicesRetrieved(message.obj);
            }
            else if (message.what == WpPrinter.MESSAGE_STATE_CHANGE) {
                Boolean stateClaimed = null;
                if (message.arg1 == WpPrinter.STATE_CONNECTED) {
                    stateClaimed = true;
                }
                else if (message.arg1 == WpPrinter.STATE_NONE) {
                    stateClaimed = false;
                }
                if (stateClaimed != null) {
                    if (isClaiming && stateClaimed) onDeviceClaimed();
                    else if (isClaiming && !stateClaimed) onFailedClaimingDevice();
                    else if (isReleasing && !stateClaimed) onDeviceReleased();
                    else if (isClaimed && !stateClaimed) onLostConnection();
                    else onUninterestedMessage(message);
                }
                else onUninterestedMessage(message);
            }
            else {
                onUninterestedMessage(message);
            }
            return true;
        }), null);
        updateUserInterface();
    }

    // 關閉應用
    private void quitApp() {
        finish();
    }

    // 應用無關的回傳
    private void onUninterestedMessage(@NonNull Message message) {
        Bundle payload = message.getData();
        byte[] received = payload.getByteArray(WpPrinter.KEY_STRING_DIRECT_IO);
        if(received != null && received.length > 0) {
            Log.i(
                    "winpos_example",
                    "message callback( " + message.what + "-" + message.arg1 +"-" + Arrays.toString(received) + " )."
            );
        }
        else if(!payload.isEmpty()) {
            Log.i(
                    "winpos_example",
                    "message callback( " + message.what + "-" + message.arg1 + "-" + payload + " )."
            );
        }
        else {
            Log.i(
                    "winpos_example",
                    "message callback( " + message.what + "-" + message.arg1 + " )."
            );
        }
    }

    // 詢問已配對藍芽裝置
    private void retrievePairedBluetoothDevices() {
        Log.i("winpos_example", "retrieving device...");
        printer.findBluetoothPrinters();
    }

    // 取得已配對裝置清單
    private void onDevicesRetrieved(Object object) {
        if (object == null) {
            Log.w("winpos_example", "no paired device found.");
            popupAlert("Device State", "No paired device found.");
        }
        else {
            Set<?> devices = (Set<?>)object;
            for(Object device : devices) {
                BluetoothDevice btDevice = (BluetoothDevice) device;
                Log.i("winpos_example", btDevice.getAddress() + " retrieved.\n");
                claimDevice(btDevice);
                break;
            }
        }
    }

    // 建立藍芽裝置連線
    private void claimDevice(BluetoothDevice device) {
        if(!isClaiming && !isReleasing && !isClaimed) {
            connectingStartTime = System.currentTimeMillis();
            address = device.getAddress();
            Log.i("winpos_example", "claiming device...");
            isClaiming = true;
            updateUserInterface();
            printer.connect(address);
        }
    }

    // 成功連線
    private void onDeviceClaimed() {
        connectingFinishTime = System.currentTimeMillis();
        Log.i("winpos_example", "device claimed( 1-2 ).\n");
        isClaiming = false;
        isClaimed = true;
        updateUserInterface();
        processPrinting();
    }

    // 連線失敗
    private void onFailedClaimingDevice() {
        connectingFinishTime = System.currentTimeMillis();
        Log.w("winpos_example", "failed claiming device( 1-0 ).\n");
        popupAlert("Device State", "Failed claiming device.");
        isClaiming = false;
        updateUserInterface();
    }

    // 順利結束連線
    private void onDeviceReleased() {
        Log.i("winpos_example", "device released( 1-0 ).\n");
        Runnable releasingTask = ()->{
            try {
                Thread.sleep(600);
                isReleasing = false;
                isClaimed = false;
                updateUserInterface();
            }
            catch (InterruptedException exception) {
                exception.printStackTrace();
            }
        };
        new Thread(releasingTask).start();
    }

    // 連線非預期中斷
    private void onLostConnection() {
        Log.e("winpos_example", "lost connection( 1-0 ).\n");
        popupAlert("Device State", "Lost connection");
        isClaimed = false;
        updateUserInterface();
    }

    // 結束藍芽裝置連線
    private void releaseDevice() {
        if(!isClaiming && !isReleasing && isClaimed) {
            Log.i("winpos_example", "releasing device...");
            isReleasing = true;
            updateUserInterface();
            printer.disconnect();
        }
    }

    // 模擬出票
    private void processPrinting() {
        Runnable printingTask = ()->{
            Log.i("winpos_example", "printing invoice.");

            if (autoGenerateInvoice) {
                printer.executeDirectIo(sampleInvoice(), true);
            }
            else {
                int limit = fakePrintingTime();
                for (int count =1; count <=limit; count++) {
                    printer.executeDirectIo(new byte[]{0x1d, 0x07, 1, 1, 1}, true);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException exception) {
                        exception.printStackTrace();
                    }
                }
            }
            if (isClaimed) {
                Log.i("winpos_example", "invoice printed.\n");
                releaseDevice();
            }
            else {
                Log.i("winpos_example", "failed printing invoice.\n");
                popupAlert("Invoice State", "Failed printing invoice.");
            }
        };
        new Thread(printingTask).start();
    }

    // 虛擬列印時長換算
    private int fakePrintingTime() {
        return binding.seekBar.getProgress();
    }

    // 範例票內容
    private byte[] sampleInvoice() {
        long connectingTime = connectingFinishTime - connectingStartTime;
        return binaryOf(
                0x1b, 0x40,
                0x1d, 0x61, 0xff,
                0x1b, 0x02,
                0x1b, 0x61, 1,
                0x1d, 0x21, 0x00,
                "Current connection to\r\n", 0x1b, 0x4a, 16,
                0x1d, 0x21, 0x11,
                address + "\r\n", 0x1b, 0x4a, 40,
                0x1d, 0x21, 0x00,
                "takes\r\n", 0x1b, 0x4a, 16,
                0x1d, 0x21, 0x44,
                String.format("%5s", connectingTime).replace(' ', '_'),
                0x1d, 0x21, 0x11,
                "ms\r\n", 0x1b, 0x4a, 160,
                0x1b, "m",
                0x1d, 0x07, 2, 1, 1
                );
    }

    // binary數據轉換
    private byte[] binaryOf(Object... items) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (Object item: items) {
            if(item instanceof Boolean) {
                Boolean flag = (Boolean)item;
                buffer.write(flag ? 1 : 0);
            }
            if(item instanceof Number) {
                Number value = (Number)item;
                buffer.write(value.byteValue());
                continue;
            }
            if(item instanceof String) {
                String text = (String)item;
                final byte[] bytes = text.getBytes();
                buffer.write(bytes,0,bytes.length);
                continue;
            }
            if(item instanceof byte[]) {
                byte[] bytes = (byte[])item;
                buffer.write(bytes,0,bytes.length);
                continue;
            }

            return new byte[]{};
        }
        return buffer.toByteArray();
    }

    // 點擊事件
    private void onButtonClicked(boolean fakePrinting) {
        autoGenerateInvoice = !fakePrinting;
        retrievePairedBluetoothDevices();
    }

    // 更新介面
    private void updateUserInterface() {
        boolean enabled = !isClaiming && !isClaimed && !isReleasing;
        runOnUiThread( () -> {
            Objects.requireNonNull(binding.button1).setEnabled(enabled);
            Objects.requireNonNull(binding.button2).setEnabled(enabled);
            Objects.requireNonNull(binding.button2).setText(String.format( Locale.ENGLISH,"FAKE PRINTING LAST FOR %d SECOND(S)", binding.seekBar.getProgress()));
            Objects.requireNonNull(binding.seekBar).setEnabled(enabled);
            Objects.requireNonNull(binding.button3).setEnabled(enabled);
        });
    }

    // 顯示訊息對話框
    private void popupAlert(String title, String message) {
        runOnUiThread(() -> {
            AlertDialog alert = builder.setMessage(message).setTitle(title).create();
            alert.show();
        });
    }
}