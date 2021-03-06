/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.coinblesk.client.wallet;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.*;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.*;
import android.widget.ProgressBar;

import com.coinblesk.bitcoin.TimeLockedAddress;
import com.coinblesk.client.CoinbleskApp;
import com.coinblesk.client.R;
import com.coinblesk.client.utils.UIUtils;
import com.coinblesk.client.ui.dialogs.QrDialogFragment;
import com.coinblesk.client.ui.dialogs.SendDialogFragment;
import com.coinblesk.client.ui.widgets.RecyclerView;
import com.coinblesk.client.config.Constants;
import com.coinblesk.payments.WalletService;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

import java.util.Collections;
import java.util.Map;


/**
 * @author Andreas Albrecht
 */
public class WalletAddressList extends Fragment
                            implements WalletAddressListAdapter.ItemClickListener,
                            CollectRefundOptionsDialog.CollectRefundOptionsListener {

    private static final String TAG = WalletAddressList.class.getName();

    private WalletService.WalletServiceBinder walletService;

    private WalletAddressListAdapter adapter;
    private RecyclerView recyclerView;
    private AsyncTask<Void, Void, TimeLockedAddress> task;
    private NetworkParameters params;

    public static Fragment newInstance() {
        return new WalletAddressList();
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        params = ((CoinbleskApp)getActivity().getApplication())
                .getAppConfig()
                .getNetworkParameters();
        Intent walletServiceIntent = new Intent(getActivity(), WalletService.class);
        getActivity().bindService(walletServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        setHasOptionsMenu(true);
        adapter = new WalletAddressListAdapter(params);
        adapter.setItemClickListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        IntentFilter walletEventFilter = new IntentFilter();
        walletEventFilter.addAction(Constants.WALLET_BALANCE_CHANGED_ACTION);
        walletEventFilter.addAction(Constants.WALLET_SCRIPTS_CHANGED_ACTION);
        LocalBroadcastManager
                .getInstance(getActivity())
                .registerReceiver(walletEventReceiver, walletEventFilter);
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager
                .getInstance(getActivity())
                .unregisterReceiver(walletEventReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
            task.cancel(false);
        }
        task = null;
        getActivity().unbindService(serviceConnection);
    }


    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.wallet_address_list, container, false);
        initView(v);
        return v;
    }

    private void initView(View v) {
        recyclerView = (RecyclerView) v.findViewById(R.id.wallet_address_list);
        recyclerView.setHasFixedSize(true);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);

        View empty = v.findViewById(R.id.wallet_address_list_empty);
        recyclerView.setEmptyView(empty);

        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_wallet_addresses, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();

        switch (id) {
            case R.id.menu_wallet_refresh:
                refreshAddresses();
                return true;
            case R.id.menu_wallet_refund:
                collectRefund();
                return true;
            case R.id.menu_wallet_addresses_create_time_locked_address:
                if (task == null) {
                    // create new task iff not already running.
                    task = createNewAddress();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private synchronized void refreshAddresses() {
        Map<Address, Coin> balances = walletService.getBalanceByAddress();
        adapter.setBalanceByAddress(balances);
        adapter.getItems().clear();
        adapter.getItems().addAll(walletService.getAddresses()); // returns sorted by [old....young]
        Collections.reverse(adapter.getItems());
        adapter.notifyDataSetChanged();
        Log.d(TAG, "Update addresses, total addresses=" + adapter.getItems().size());
    }

    private void collectRefund() {
        final Coin refundBalance = walletService.getBalanceUnlocked();
        Log.d(TAG, "Collect refund - available amount (unlocked): " + refundBalance.getValue());
        if (refundBalance.isPositive()) {
            showCollectRefundOptionsDialog(refundBalance);
        } else {
            showAmountTooSmallDialog(refundBalance);
        }
    }

    private void showCollectRefundOptionsDialog(Coin amount) {
        // there are two options for the user:
        // (1) user pays to current receive address of this wallet, i.e. in order to lock the funds again.
        // (2) user pays to custom address (external wallet)
        DialogFragment optionsDialog = CollectRefundOptionsDialog.newInstance(amount);
        optionsDialog.setTargetFragment(this, 0);
        optionsDialog.show(getFragmentManager(), "collect_refund_options_dialog");
    }

    private void showSendDialog(Coin amount) {
        DialogFragment sendDialog = SendDialogFragment.newInstance(amount);
        sendDialog.show(getFragmentManager(), "collect_refund_send_dialog");
        // Note: the callback is WalletActivity#sendCoins
    }

    private void showAmountTooSmallDialog(Coin amount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.AlertDialogAccent);
        AlertDialog dialog = builder
                .setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setTitle(R.string.dialog_refund_too_small_title)
                .setMessage(getString(R.string.dialog_refund_too_small_message, amount.toFriendlyString()))
                .create();
        dialog.show();
    }

    private AsyncTask<Void, Void, TimeLockedAddress> createNewAddress() {
        Log.d(TAG, "Create new address.");
        return new CreateAddressTask().execute();
    }

    @Override
    public void onItemClick(TimeLockedAddress item, int position) {
        try {
            Address address = item.getAddress(params);
            String uri = BitcoinURI.convertToBitcoinURI(address, null, null, null);
            BitcoinURI addressUri = new BitcoinURI(uri);
            QrDialogFragment
                    .newInstance(addressUri)
                    .show(getFragmentManager(), "address_qr_fragment");
        } catch (BitcoinURIParseException e) {
            Log.w(TAG, "Could not create bitcoin uri: ", e);
        }
    }

    private ProgressBar getProgressBar() {
        View progressBar = null;
        if (getActivity() != null && !getActivity().isDestroyed()) {
            progressBar = getActivity().findViewById(R.id.wallet_progressBar);
        }
        return (progressBar != null) ? (ProgressBar) progressBar : null;
    }

    private void startProgress() {
        ProgressBar progressBar = getProgressBar();
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void stopProgress() {
        ProgressBar progressBar = getProgressBar();
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onTopUpOptionSelected() {
        final Coin refundBalance = walletService.getBalanceUnlocked();
        Address currentReceiveAddress = walletService.getCurrentReceiveAddress();
        ((WalletActivity) getActivity()).collectRefund(currentReceiveAddress);
    }

    @Override
    public void onSendOptionSelected() {
        final Coin refundBalance = walletService.getBalanceUnlocked();
        showSendDialog(refundBalance);
    }

    private class CreateAddressTask extends AsyncTask<Void, Void, TimeLockedAddress> {

        private Exception thrownException;

        @Override
        protected void onPreExecute () {
            // runs on UI thread
            startProgress();
        }

        @Override
        protected TimeLockedAddress doInBackground(Void... params) {
            // runs on background thread
            TimeLockedAddress newAddress;
            try {
                newAddress = walletService.createTimeLockedAddress();
            } catch (Exception e) {
                thrownException = e;
                Log.w(TAG, "Could not create new address: ", e);
                newAddress = null;
            }
            return newAddress;
        }

        @Override
        protected void onPostExecute (TimeLockedAddress newAddress) {
            if (getActivity() == null || getActivity().isDestroyed()) {
                // may happen if user goes back before task completed.
                return;
            }

            // runs on UI thread
            stopProgress();
            task = null;

            if (thrownException != null || newAddress == null) {
                String errorMsg = (thrownException != null) ? thrownException.getMessage() : "unknown";
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AlertDialogAccent);
                builder
                        .setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setTitle(R.string.wallet_create_address_failed_title)
                        .setMessage(getString(R.string.wallet_create_address_failed_message, errorMsg))
                        .create()
                        .show();
            } else if (newAddress != null) {
                Address address = newAddress.getAddress(params);
                long lockTime = newAddress.getLockTime();
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AlertDialogAccent);
                builder
                        .setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setTitle(R.string.wallet_create_address_success_title)
                        .setMessage(getString(R.string.wallet_create_address_success_message,
                                address.toBase58(), UIUtils.lockedUntilText(lockTime)))
                        .create()
                        .show();
            }
        }

        @Override
        protected void onCancelled (TimeLockedAddress result) {
            stopProgress();
            task = null;
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder binder) {
            walletService = (WalletService.WalletServiceBinder) binder;
            refreshAddresses();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            walletService = null;
        }
    };

    private final BroadcastReceiver walletEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshAddresses();
        }
    };

}