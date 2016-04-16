package com.coinblesk.payments.communications.peers.steps;

import android.util.Log;

import com.coinblesk.json.TxSig;
import com.coinblesk.json.VerifyTO;
import com.coinblesk.payments.Constants;
import com.coinblesk.payments.communications.http.CoinbleskWebService;
import com.coinblesk.payments.communications.messages.DERInteger;
import com.coinblesk.payments.communications.messages.DERObject;
import com.coinblesk.payments.communications.messages.DERSequence;
import com.coinblesk.util.Pair;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Alessandro De Carli (@a_d_c_) on 28/02/16.
 * Papers.ch
 * a.decarli@papers.ch
 */
public class PaymentFinalSignatureOutpointsReceiveStep implements Step {
    private final static String TAG = PaymentFinalSignatureOutpointsReceiveStep.class.getSimpleName();


    private final ECKey multisigClientKey;
    private Transaction fullSignedTransaction;
    private final List<TxSig> serverSignatures;
    private final Address recipientAddress;
    private final Coin amount;

    public PaymentFinalSignatureOutpointsReceiveStep(ECKey multisigClientKey, List<TxSig> serverSignatures, Address recipientAddress, Coin amount) {
        this.multisigClientKey = multisigClientKey;
        this.serverSignatures = serverSignatures;
        this.recipientAddress = recipientAddress;
        this.amount = amount;
    }

    @Override
    public DERObject process(DERObject input) {
        try {


            final DERSequence derSequence = (DERSequence) input;

            List<Pair<byte[], Long>> outpointCoinPairs = new ArrayList<Pair<byte[], Long>>();
            for (DERObject outpointCoinPair : ((DERSequence) derSequence.getChildren().get(0)).getChildren()) {
                DERSequence outpointCoinSequence = (DERSequence) outpointCoinPair;
                outpointCoinPairs.add(new Pair<byte[], Long>(outpointCoinSequence.getChildren().get(0).getPayload(), ((DERInteger) outpointCoinSequence.getChildren().get(1)).getBigInteger().longValue()));
            }

            List<TxSig> clientSignatures = new ArrayList<TxSig>();
            for (DERObject clientSignature : ((DERSequence) derSequence.getChildren().get(1)).getChildren()) {
                DERSequence clientSignatureSequence = (DERSequence) clientSignature;
                TxSig txSig = new TxSig();
                txSig.sigR(((DERInteger) clientSignatureSequence.getChildren().get(0)).getBigInteger().toString());
                txSig.sigS(((DERInteger) clientSignatureSequence.getChildren().get(1)).getBigInteger().toString());
                clientSignatures.add(txSig);
            }


            final BigInteger timestamp = ((DERInteger) derSequence.getChildren().get(2)).getBigInteger();
            final TxSig txSig = new TxSig();
            txSig.sigR(((DERInteger) derSequence.getChildren().get(3)).getBigInteger().toString());
            txSig.sigS(((DERInteger) derSequence.getChildren().get(4)).getBigInteger().toString());

            VerifyTO completeSignTO = new VerifyTO()
                    .clientPublicKey(multisigClientKey.getPubKey())
                    .p2shAddressTo(recipientAddress.toString())
                    .amountToSpend(amount.value)
                    .clientSignatures(clientSignatures)
                    .serverSignatures(serverSignatures)
                    .outpointsCoinPair(outpointCoinPairs)
                    .currentDate(timestamp.longValue());

            final CoinbleskWebService service = Constants.RETROFIT.create(CoinbleskWebService.class);
            VerifyTO responseCompleteSignTO = service.verify(completeSignTO).execute().body();
            Log.d(TAG, "instant payment was " + responseCompleteSignTO.type());
            fullSignedTransaction = new Transaction(Constants.PARAMS, responseCompleteSignTO.transaction());
            return DERObject.NULLOBJECT;
        } catch (IOException e) {
            Log.e(TAG, "Exception in the signature step: ", e);
        }

        return null;
    }

    public Transaction getFullSignedTransaction() {
        return fullSignedTransaction;
    }
}
