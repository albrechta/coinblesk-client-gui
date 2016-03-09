package ch.papers.payments.communications.peers.handlers;

import android.util.Log;

import org.bitcoinj.uri.BitcoinURI;

import java.io.InputStream;
import java.io.OutputStream;

import ch.papers.payments.communications.peers.PaymentRequestAuthorizer;
import ch.papers.payments.communications.peers.steps.PaymentAuthorizationReceiveStep;
import ch.papers.payments.communications.peers.steps.PaymentFinalSignatureReceiveStep;
import ch.papers.payments.communications.peers.steps.PaymentRefundReceiveStep;
import ch.papers.payments.communications.peers.steps.PaymentRequestSendStep;

/**
 * Created by Alessandro De Carli (@a_d_c_) on 04/03/16.
 * Papers.ch
 * a.decarli@papers.ch
 */
public class InstantPaymentServerHandler extends DERObjectStreamHandler {
    private final static String TAG = InstantPaymentServerHandler.class.getSimpleName();

    private final BitcoinURI paymentUri;
    private final PaymentRequestAuthorizer paymentRequestAuthorizer;

    public InstantPaymentServerHandler(InputStream inputStream, OutputStream outputStream, BitcoinURI paymentUri, PaymentRequestAuthorizer paymentRequestAuthorizer) {
        super(inputStream,outputStream);
        this.paymentUri = paymentUri;
        this.paymentRequestAuthorizer = paymentRequestAuthorizer;
    }


    @Override
    public void run() {
        try {
            long startTime = System.currentTimeMillis();
            final PaymentRequestSendStep paymentRequestSendStep = new PaymentRequestSendStep(paymentUri);
            writeDERObject(paymentRequestSendStep.process(readDERObject()));

            final PaymentAuthorizationReceiveStep paymentAuthorizationReceiveStep = new PaymentAuthorizationReceiveStep(this.paymentUri);
            writeDERObject(paymentAuthorizationReceiveStep.process(readDERObject()));

            final PaymentRefundReceiveStep paymentRefundReceiveStep = new PaymentRefundReceiveStep(paymentAuthorizationReceiveStep.getClientPublicKey());
            writeDERObject(paymentRefundReceiveStep.process(readDERObject()));

            final PaymentFinalSignatureReceiveStep paymentFinalSignatureReceiveStep = new PaymentFinalSignatureReceiveStep(paymentAuthorizationReceiveStep.getClientPublicKey(), this.paymentUri.getAddress());
            paymentFinalSignatureReceiveStep.process(readDERObject());

            paymentRequestAuthorizer.onPaymentSuccess();
            Log.d(TAG, "payment was successful in " + (startTime - System.currentTimeMillis()));
        } catch (Exception e){
            paymentRequestAuthorizer.onPaymentError(e.getMessage());
        }
    }


}
