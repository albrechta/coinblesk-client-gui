package ch.papers.payments;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.coinblesk.json.CompleteSignTO;
import com.coinblesk.json.KeyTO;
import com.coinblesk.json.PrepareHalfSignTO;
import com.coinblesk.json.RefundTO;
import com.coinblesk.util.BitcoinUtils;
import com.coinblesk.util.SerializeUtils;
import com.google.common.collect.ImmutableList;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.bitstamp.BitstampExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.service.polling.marketdata.PollingMarketDataService;

import org.bitcoinj.core.AbstractWalletEventListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DownloadProgressTracker;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;

import ch.papers.objectstorage.UuidObjectStorage;
import ch.papers.objectstorage.UuidObjectStorageException;
import ch.papers.objectstorage.filters.MatchAllFilter;
import ch.papers.objectstorage.listeners.DummyOnResultListener;
import ch.papers.payments.communications.http.CoinbleskWebService;
import ch.papers.payments.models.ECKeyWrapper;
import ch.papers.payments.models.ExchangeRateWrapper;
import ch.papers.payments.models.TransactionWrapper;
import ch.papers.payments.models.filters.ECKeyWrapperFilter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by Alessandro De Carli (@a_d_c_) on 14/02/16.
 * Papers.ch
 * a.decarli@papers.ch
 */
public class WalletService extends Service {

    private final static String TAG = WalletService.class.getName();

    private final Retrofit retrofit = new Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create(SerializeUtils.GSON))
            .baseUrl(Constants.COINBLESK_SERVER_BASE_URL)
            .build();

    private String fiatCurrency = "USD";
    private ExchangeRate exchangeRate = new ExchangeRate(Fiat.parseFiat("CHF", "430"));
    private WalletAppKit kit;

    private Script multisigAddressScript;
    private ECKey multisigClientKey;
    private ECKey multisigServerKey;

    public class WalletServiceBinder extends Binder {

        public WalletServiceBinder() {
            try {
                exchangeRate = UuidObjectStorage.getInstance().getFirstMatchEntry(new MatchAllFilter(),ExchangeRateWrapper.class).getExchangeRate();
            } catch (UuidObjectStorageException e) {
                Log.d(TAG,"could not retrieve old exchangerate from storage, staying with preshiped default");
            }
        }

        public ExchangeRate getExchangeRate() {
            return exchangeRate;
        }

        public Address getCurrentReceiveAddress() {
            if (multisigAddressScript != null) {
                return multisigAddressScript.getToAddress(Constants.PARAMS);
            } else {
                return WalletService.this.kit.wallet().currentReceiveAddress();
            }
        }

        public void setCurrency(String currency) {
            fiatCurrency = currency;
            this.fetchExchangeRate();
        }

        public Coin getBalance() {
            return WalletService.this.kit.wallet().getBalance();
        }

        public Fiat getBalanceFiat() {
            return WalletService.this.exchangeRate.coinToFiat(WalletService.this.kit.wallet().getBalance());
        }

        public List<TransactionWrapper> getTransactionsByTime() {
            final List<TransactionWrapper> transactions = new ArrayList<TransactionWrapper>();
            for (Transaction transaction : WalletService.this.kit.wallet().getTransactionsByTime()) {
                transaction.setExchangeRate(getExchangeRate());
                transactions.add(new TransactionWrapper(transaction, WalletService.this.kit.wallet()));
            }
            return transactions;
        }

        public List<TransactionOutput> getUnspentInstantOutputs() {
            List<TransactionOutput> unspentInstantOutputs = new ArrayList<TransactionOutput>();
            for (TransactionOutput unspentTransactionOutput : kit.wallet().calculateAllSpendCandidates(true, false)) {
                if (unspentTransactionOutput.getScriptPubKey().getToAddress(Constants.PARAMS).equals(this.getCurrentReceiveAddress())) {
                    unspentInstantOutputs.add(unspentTransactionOutput);
                }
            }
            return unspentInstantOutputs;
        }

        public void instantSendCoins(final Address address, final Coin amount) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final CoinbleskWebService service = retrofit.create(CoinbleskWebService.class);
                        // let server sign first
                        final PrepareHalfSignTO prepareHalfSignTO = new PrepareHalfSignTO()
                                .amountToSpend(amount.longValue())
                                .clientPublicKey(multisigClientKey.getPubKey())
                                .p2shAddressTo(address.toString())
                                .messageSig(null)
                                .currentDate(System.currentTimeMillis());
                        SerializeUtils.sign(prepareHalfSignTO, multisigClientKey);


                        Response<PrepareHalfSignTO> prepareHalfSignTOResponse = service.prepareHalfSign(prepareHalfSignTO).execute();
                        final PrepareHalfSignTO serverHalfSignTO = prepareHalfSignTOResponse.body();

                        // now let us sign and verify
                        final List<TransactionOutput> unspentTransactionOutputs = getUnspentInstantOutputs();
                        final Transaction transaction = BitcoinUtils.createTx(Constants.PARAMS, unspentTransactionOutputs, getCurrentReceiveAddress(), address, amount.longValue());
                        Log.d(TAG, "rcv: " + address);
                        Log.d(TAG, "tx: " + transaction);
                        //This is needed because otherwise we mix up signature order
                        List<ECKey> keys = new ArrayList<ECKey>();
                        keys.add(multisigClientKey);
                        keys.add(multisigServerKey);
                        Collections.sort(keys, ECKey.PUBKEY_COMPARATOR);

                        final Script redeemScript = ScriptBuilder.createRedeemScript(2, keys);
                        Log.d(TAG, transaction.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false).toString());
                        final List<TransactionSignature> clientTransactionSignatures = BitcoinUtils.partiallySign(transaction, redeemScript, multisigClientKey);
                        final List<TransactionSignature> serverTransactionSignatures = SerializeUtils.deserializeSignatures(serverHalfSignTO.signatures());

                        for (int i = 0; i < clientTransactionSignatures.size(); i++) {
                            final TransactionSignature serverSignature = serverTransactionSignatures.get(i);
                            final TransactionSignature clientSignature = clientTransactionSignatures.get(i);

                            // yes, because order matters...
                            List<TransactionSignature> signatures = keys.indexOf(multisigClientKey) == 0 ? ImmutableList.of(clientSignature, serverSignature) : ImmutableList.of(serverSignature, clientSignature);
                            Script p2SHMultiSigInputScript = ScriptBuilder.createP2SHMultiSigInputScript(signatures, redeemScript);
                            transaction.getInput(i).setScriptSig(p2SHMultiSigInputScript);
                            transaction.getInput(i).verify();
                        }

                        int changeOutput = -1;
                        for (TransactionOutput transactionOutput : transaction.getOutputs()) {
                            if (transactionOutput.getAddressFromP2SH(Constants.PARAMS) != null && transactionOutput.getAddressFromP2SH(Constants.PARAMS).equals(getCurrentReceiveAddress())) {
                                changeOutput = transaction.getOutputs().indexOf(transactionOutput);
                                break;
                            }
                        }

                        if(changeOutput >= 0){
                            // generate refund
                            final Transaction refundTransaction = PaymentProtocol.getInstance().generateRefundTransaction(transaction.getOutput(changeOutput),multisigClientKey.toAddress(Constants.PARAMS));
                            final List<TransactionSignature> clientRefundTransactionSignatures = BitcoinUtils.partiallySign(refundTransaction, redeemScript, multisigClientKey);

                            RefundTO refundTO = new RefundTO();
                            refundTO.clientPublicKey(multisigClientKey.getPubKey());
                            refundTO.clientSignatures(SerializeUtils.serializeSignatures(clientRefundTransactionSignatures));
                            refundTO.refundTransaction(refundTransaction.unsafeBitcoinSerialize());
                            refundTO.currentDate(System.currentTimeMillis());
                            if (refundTO.messageSig() == null) {
                                SerializeUtils.sign(refundTO, multisigClientKey);
                            }


                            // let server sign
                            final RefundTO serverRefundTO = service.refund(refundTO).execute().body();
                            final List<TransactionSignature> serverRefundTransactionSignatures = SerializeUtils.deserializeSignatures(serverRefundTO.serverSignatures());
                            for (int i = 0; i < clientRefundTransactionSignatures.size(); i++) {
                                final TransactionSignature serverSignature = serverRefundTransactionSignatures.get(i);
                                final TransactionSignature clientSignature = clientRefundTransactionSignatures.get(i);

                                // yes, because order matters...
                                List<TransactionSignature> signatures = keys.indexOf(multisigClientKey)==0 ? ImmutableList.of(clientSignature,serverSignature) : ImmutableList.of(serverSignature,clientSignature);
                                Script p2SHMultiSigInputScript = ScriptBuilder.createP2SHMultiSigInputScript(signatures, redeemScript);
                                refundTransaction.getInput(i).setScriptSig(p2SHMultiSigInputScript);
                                refundTransaction.getInput(i).verify();
                            }
                        }


                        CompleteSignTO completeSignTO = new CompleteSignTO()
                                .clientPublicKey(multisigClientKey.getPubKey())
                                .p2shAddressTo(address.toString())
                                .fullSignedTransaction(transaction.unsafeBitcoinSerialize())
                                .currentDate(System.currentTimeMillis());
                        if (completeSignTO.messageSig() == null) {
                            SerializeUtils.sign(completeSignTO, multisigClientKey);
                        }

                        CompleteSignTO responseCompleteSignTO = service.sign(completeSignTO).execute().body();
                        Log.d(TAG,"instant payment was "+responseCompleteSignTO.type());
                        switch (responseCompleteSignTO.type().nr()){
                            case 1:
                                final Intent instantPaymentSuccesful = new Intent(Constants.INSTANT_PAYMENT_SUCCESSFUL_ACTION);
                                LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(instantPaymentSuccesful);
                                break;
                            default:
                                final Intent instantPaymentFailed = new Intent(Constants.INSTANT_PAYMENT_FAILED_ACTION);
                                instantPaymentFailed.putExtra(Constants.ERROR_MESSAGE_KEY,responseCompleteSignTO.message());
                                LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(instantPaymentFailed);
                                break;
                        }
                        // all good our refund tx is safe, we can broadcast
                        kit.peerGroup().broadcastTransaction(transaction);
                    } catch (Exception e) {
                        final Intent instantPaymentFailed = new Intent(Constants.INSTANT_PAYMENT_FAILED_ACTION);
                        instantPaymentFailed.putExtra(Constants.ERROR_MESSAGE_KEY,e.getMessage());
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(instantPaymentFailed);
                    }
                }
            }).start();
        }

        public void sendCoins(Address address, Coin amount) {
            if (multisigAddressScript != null && getUnspentInstantOutputs().size() > 0) {
                instantSendCoins(address, amount);
            } else {
                try {
                    final Transaction transaction = WalletService.this.kit.wallet().createSend(address, amount);
                    WalletService.this.kit.peerGroup().broadcastTransaction(transaction);
                } catch (InsufficientMoneyException e) {
                    final Intent walletInsufficientBalanceIntent = new Intent(Constants.WALLET_INSUFFICIENT_BALANCE_ACTION);
                    LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletInsufficientBalanceIntent);
                }
            }
        }

        public TransactionWrapper getTransaction(String transactionHash) {
            return new TransactionWrapper(WalletService.this.kit.wallet().getTransaction(Sha256Hash.wrap(transactionHash)), WalletService.this.kit.wallet());
        }

        public Address getRefundAddress() {
            return WalletService.this.kit.wallet().currentReceiveAddress();
        }

        public ECKey getMultisigClientKey() {
            return multisigClientKey;
        }

        public Script getMultisigAddressScript() {
            return multisigAddressScript;
        }

        public ECKey getMultisigServerKey() {
            return multisigServerKey;
        }

        public void fetchExchangeRate() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Exchange bitstamp = ExchangeFactory.INSTANCE.createExchange(BitstampExchange.class.getName());
                        PollingMarketDataService marketDataService = bitstamp.getPollingMarketDataService();
                        Ticker ticker;
                        if (fiatCurrency.equals("USD")) {
                            ticker = marketDataService.getTicker(CurrencyPair.BTC_USD);
                        } else if (fiatCurrency.equals("CHF")) {
                            ticker = marketDataService.getTicker(CurrencyPair.BTC_CHF);
                        } else {
                            ticker = marketDataService.getTicker(CurrencyPair.BTC_EUR);
                        }

                        WalletService.this.exchangeRate = new ExchangeRate(Fiat.valueOf(ticker.getCurrencyPair().counterSymbol, ticker.getAsk().longValue() * 10000));
                        Intent walletProgressIntent = new Intent(Constants.WALLET_BALANCE_CHANGED_ACTION);
                        walletProgressIntent.putExtra("balance", kit.wallet().getBalance().value);
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletProgressIntent);
                        Intent exchangeRateChangeIntent = new Intent(Constants.EXCHANGE_RATE_CHANGED_ACTION);
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(exchangeRateChangeIntent);

                        UuidObjectStorage.getInstance().deleteEntries(new MatchAllFilter(), ExchangeRateWrapper.class);
                        UuidObjectStorage.getInstance().addEntry(new ExchangeRateWrapper(exchangeRate), ExchangeRateWrapper.class);
                        UuidObjectStorage.getInstance().commit();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private final WalletServiceBinder walletServiceBinder = new WalletServiceBinder();


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        UuidObjectStorage.getInstance().init(this.getFilesDir());
        LogManager.getLogManager().getLogger("").setLevel(Level.SEVERE);
        this.kit = new WalletAppKit(Constants.PARAMS, this.getFilesDir(), Constants.WALLET_FILES_PREFIX) {

            @Override
            protected void onSetupCompleted() {
                if (wallet().getKeychainSize() < 1) {
                    ECKeyWrapper walletKey;
                    try {
                        walletKey = UuidObjectStorage.getInstance().getFirstMatchEntry(new ECKeyWrapperFilter(Constants.WALLET_KEY_NAME), ECKeyWrapper.class);
                    } catch (UuidObjectStorageException e) {
                        walletKey = new ECKeyWrapper(new ECKey().getPrivKeyBytes(), Constants.WALLET_KEY_NAME);
                        try {
                            UuidObjectStorage.getInstance().addEntry(walletKey, ECKeyWrapper.class);
                            UuidObjectStorage.getInstance().commit();
                        } catch (UuidObjectStorageException e1) {
                            Log.d(TAG, "couldn't store freshly generated ECKey: " + e.getMessage());
                        }
                    }
                    wallet().importKey(walletKey.getKey());
                }

                kit.wallet().addEventListener(new AbstractWalletEventListener() {
                    @Override
                    public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                        super.onCoinsSent(wallet, tx, prevBalance, newBalance);
                        Intent walletProgressIntent = new Intent(Constants.WALLET_BALANCE_CHANGED_ACTION);
                        walletProgressIntent.putExtra("balance", newBalance.value);
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletProgressIntent);

                        Intent walletCoinsSentIntent = new Intent(Constants.WALLET_COINS_SENT_ACTION);
                        walletCoinsSentIntent.putExtra("balance", newBalance.value);
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletCoinsSentIntent);
                    }

                    @Override
                    public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                        super.onScriptsChanged(wallet, scripts, isAddingScripts);
                        Intent walletProgressIntent = new Intent(Constants.WALLET_SCRIPTS_CHANGED_ACTION);
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletProgressIntent);
                    }

                    @Override
                    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                        super.onTransactionConfidenceChanged(wallet, tx);
                        Intent walletProgressIntent = new Intent(Constants.WALLET_TRANSACTIONS_CHANGED_ACTION);
                        walletProgressIntent.putExtra("transactionHash", tx.getHashAsString());
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletProgressIntent);
                    }

                    @Override
                    public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                        Intent walletProgressIntent = new Intent(Constants.WALLET_BALANCE_CHANGED_ACTION);
                        walletProgressIntent.putExtra("balance", newBalance.value);
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletProgressIntent);


                        Intent walletCoinsReceivedIntent = new Intent(Constants.WALLET_COINS_RECEIVED_ACTION);
                        walletCoinsReceivedIntent.putExtra("balance", newBalance.value);
                        LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletCoinsReceivedIntent);
                    }
                });

                try {
                    ECKeyWrapper serverKey = UuidObjectStorage.getInstance().getFirstMatchEntry(new ECKeyWrapperFilter(Constants.MULTISIG_SERVER_KEY_NAME), ECKeyWrapper.class);
                    ECKeyWrapper clientKey = UuidObjectStorage.getInstance().getFirstMatchEntry(new ECKeyWrapperFilter(Constants.MULTISIG_CLIENT_KEY_NAME), ECKeyWrapper.class);
                    setupMultiSigAddress(clientKey.getKey(), serverKey.getKey());
                } catch (UuidObjectStorageException e) {
                    try {
                        CoinbleskWebService service = retrofit.create(CoinbleskWebService.class);
                        final ECKey clientMultiSigKey = new ECKey();
                        final KeyTO clientKey = new KeyTO();
                        clientKey.publicKey(clientMultiSigKey.getPubKey());
                        Response<KeyTO> response = service.keyExchange(clientKey).execute();
                        if (response.isSuccess()) {
                            final KeyTO serverKey = response.body();
                            final ECKey serverMultiSigKey = ECKey.fromPublicOnly(serverKey.publicKey());
                            UuidObjectStorage.getInstance().addEntry(new ECKeyWrapper(clientMultiSigKey.getPrivKeyBytes(), Constants.MULTISIG_CLIENT_KEY_NAME), ECKeyWrapper.class);
                            UuidObjectStorage.getInstance().addEntry(new ECKeyWrapper(serverMultiSigKey.getPubKey(), Constants.MULTISIG_SERVER_KEY_NAME, true), ECKeyWrapper.class);
                            UuidObjectStorage.getInstance().commit();
                            setupMultiSigAddress(clientMultiSigKey, serverMultiSigKey);
                        } else {
                            Log.d(TAG, "error during key setup:"+response.code());
                        }
                    } catch (Exception e2) {
                        Log.d(TAG, "error while setting up multisig address:" + e2.getMessage());
                    }
                }


                try {
                    kit.peerGroup().addAddress(Inet4Address.getByName("144.76.175.228"));
                    kit.peerGroup().addAddress(Inet4Address.getByName("88.198.20.152"));
                    kit.peerGroup().addAddress(Inet4Address.getByName("52.4.156.236"));
                    kit.peerGroup().addAddress(Inet4Address.getByName("176.9.24.110"));
                    kit.peerGroup().addAddress(Inet4Address.getByName("144.76.175.228"));
                } catch (IOException e) {

                }
            }
        };

        kit.setDownloadListener(new DownloadProgressTracker() {

            @Override
            public void onChainDownloadStarted(Peer peer, int blocksLeft) {
                super.onChainDownloadStarted(peer, blocksLeft);
                Log.d(TAG, "started download of block:" + blocksLeft);
            }

            @Override
            public void progress(double pct, int blocksSoFar, Date date) {
                super.progress(pct, blocksSoFar, date);
                Intent walletProgressIntent = new Intent(Constants.WALLET_PROGRESS_ACTION);
                walletProgressIntent.putExtra("progress", pct);
                walletProgressIntent.putExtra("blocksSoFar", blocksSoFar);
                walletProgressIntent.putExtra("date", date);
                LocalBroadcastManager.getInstance(WalletService.this).sendBroadcast(walletProgressIntent);
                Log.d(TAG, "progress " + pct);
            }

            @Override
            protected void doneDownload() {
                super.doneDownload();
                Log.d(TAG, "done download");
            }
        });

        try {

            kit.setCheckpoints(this.getAssets().open("checkpoints-testnet"));
        } catch (IOException e) {

        }

        kit.setBlockingStartup(false);
        kit.startAsync().awaitRunning();
        //clearMultisig();

        Log.d(TAG, "wallet started");
        ((ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.ERROR);;
        return Service.START_NOT_STICKY;
    }

    private void setupMultiSigAddress(ECKey clientKey, ECKey serverKey) {
        this.multisigServerKey = serverKey;
        this.multisigClientKey = clientKey;
        this.multisigAddressScript = ScriptBuilder.createP2SHOutputScript(2, ImmutableList.of(clientKey, serverKey));

        for (Script watchedScript : kit.wallet().getWatchedScripts()) {
            if (!watchedScript.getToAddress(Constants.PARAMS).equals(multisigAddressScript.getToAddress(Constants.PARAMS))) {
                kit.wallet().removeWatchedScripts(ImmutableList.<Script>of(watchedScript));
            }
        }

        // now add the right one
        kit.wallet().addWatchedScripts(ImmutableList.of(multisigAddressScript));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.kit.stopAsync().awaitTerminated();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "on bind");
        return this.walletServiceBinder;
    }

    private void clearMultisig() {
        UuidObjectStorage.getInstance().deleteEntries(new ECKeyWrapperFilter(Constants.MULTISIG_CLIENT_KEY_NAME), DummyOnResultListener.getInstance(), ECKeyWrapper.class);
        UuidObjectStorage.getInstance().deleteEntries(new ECKeyWrapperFilter(Constants.MULTISIG_SERVER_KEY_NAME), DummyOnResultListener.getInstance(), ECKeyWrapper.class);
        UuidObjectStorage.getInstance().commit(DummyOnResultListener.getInstance());
    }
}
