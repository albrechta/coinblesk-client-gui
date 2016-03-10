package ch.papers.payments.communications.peers.nfc;

/**
 * Created by draft on 10.03.16.
 */
public interface NFCClientACSCallback {
    void tagDiscovered(NFCClientACS.ACSTransceiver transceiver);

    void tagFailed();

    void nfcTagLost();
}
