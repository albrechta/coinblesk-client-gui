package ch.papers.payments.communications.peers.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import ch.papers.objectstorage.listeners.OnResultListener;
import ch.papers.payments.Constants;
import ch.papers.payments.WalletService;
import ch.papers.payments.communications.peers.AbstractClient;
import ch.papers.payments.communications.peers.handlers.DHKeyExchangeClientHandler;
import ch.papers.payments.communications.peers.handlers.InstantPaymentClientHandler;

/**
 * Created by Alessandro De Carli (@a_d_c_) on 27/02/16.
 * Papers.ch
 * a.decarli@papers.ch
 */
public class BluetoothRFCommClient extends AbstractClient {
    private final static String TAG = BluetoothRFCommClient.class.getSimpleName();


    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private BluetoothSocket socket;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action) && socket != null) {
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "device found:" + device.getAddress());
                        try {
                            final BluetoothSocket deviceSocket = device.createInsecureRfcommSocketToServiceRecord(Constants.SERVICE_UUID);
                            deviceSocket.connect();
                            new DHKeyExchangeClientHandler(deviceSocket.getInputStream(), deviceSocket.getOutputStream(), new OnResultListener<SecretKeySpec>() {
                                @Override
                                public void onSuccess(SecretKeySpec secretKeySpec) {
                                    Log.d(TAG, "exchange successful");
                                    commonSecretKeySpec = secretKeySpec;
                                    socket = deviceSocket;
                                    if (isReadyForInstantPayment()) {
                                        onIsReadyForInstantPaymentChange();
                                    }
                                }

                                @Override
                                public void onError(String s) {
                                    Log.d(TAG, "error during key exchange:" + s);
                                }
                            }).run();
                        } catch (Exception e) {
                        }
                    }
                }).start();

            }
        }
    };
    private SecretKeySpec commonSecretKeySpec;

    public BluetoothRFCommClient(Context context, WalletService.WalletServiceBinder walletServiceBinder) {
        super(context, walletServiceBinder);
    }

    @Override
    public void onIsReadyForInstantPaymentChange() {
        if (this.isReadyForInstantPayment() && commonSecretKeySpec != null) {
            try {
                final byte[] iv = new byte[16];
                Arrays.fill(iv, (byte) 0x00);
                IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

                final Cipher writeCipher = Cipher.getInstance(Constants.SYMMETRIC_CIPHER_MODE);
                writeCipher.init(Cipher.ENCRYPT_MODE, commonSecretKeySpec,ivParameterSpec);

                final Cipher readCipher = Cipher.getInstance(Constants.SYMMETRIC_CIPHER_MODE);
                readCipher.init(Cipher.DECRYPT_MODE, commonSecretKeySpec,ivParameterSpec);

                final OutputStream encrytpedOutputStream = new CipherOutputStream(socket.getOutputStream(), writeCipher);
                final InputStream encryptedInputStream = new CipherInputStream(socket.getInputStream(), readCipher);
                Log.d(TAG,"setting up secure connection");
                new Thread(new InstantPaymentClientHandler(encryptedInputStream, encrytpedOutputStream, getWalletServiceBinder(), getPaymentRequestAuthorizer())).start();
                setRunning(true);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
        } else {
            //TODO block
        }
    }

    @Override
    public void start() {
        if (!this.bluetoothAdapter.isEnabled()) {
            this.bluetoothAdapter.enable();
        }

        Log.d(TAG, "starting discovery");

        final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.getContext().registerReceiver(this.broadcastReceiver, filter);
        this.bluetoothAdapter.startDiscovery();
    }

    @Override
    public void stop() {
        this.setRunning(false);
        try {
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isSupported() {
        return this.bluetoothAdapter != null;
    }
}
