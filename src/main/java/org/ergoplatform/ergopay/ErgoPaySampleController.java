package org.ergoplatform.ergopay;

import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.BoxOperations;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.appkit.ReducedTransaction;
import org.ergoplatform.appkit.RestApiErgoClient;
import org.ergoplatform.appkit.UnsignedTransaction;
import org.ergoplatform.appkit.impl.ErgoTreeContract;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Collections;

@RestController
public class ErgoPaySampleController {
    public static final String NODE_MAINNET = "http://213.239.193.208:9053/";
    public static final String NODE_TESTNET = "http://213.239.193.208:9052/";

    @GetMapping("/roundTrip/{address}")
    public ErgoPayResponse roundTrip(@PathVariable String address) {
        ErgoPayResponse response = new ErgoPayResponse();

        try {
            boolean isMainNet = isMainNetAddress(address);
            long amountToSend = 1000L * 1000L * 1000L;
            Address sender = Address.create(address);
            Address recipient = Address.create(address);

            byte[] reduced = getReducedSendTx(isMainNet, amountToSend, sender, recipient).toBytes();

            response.reducedTx = Base64.getUrlEncoder().encodeToString(reduced);
            response.address = address;
            response.message = "Here is your 1 ERG round trip.";
            response.messageSeverity = ErgoPayResponse.Severity.INFORMATION;

        } catch (Throwable t) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR;
            response.message = (t.getMessage());
        }

        return response;
    }

    private ReducedTransaction getReducedSendTx(boolean isMainNet, long amountToSend, Address sender, Address recipient) {
        NetworkType networkType = isMainNet ? NetworkType.MAINNET : NetworkType.TESTNET;
        return RestApiErgoClient.create(
                getDefaultNodeUrl(isMainNet),
                networkType,
                "",
                RestApiErgoClient.getDefaultExplorerUrl(networkType)
        ).execute(ctx -> {
            ErgoTreeContract contract = new ErgoTreeContract(recipient.getErgoAddress().script());
            UnsignedTransaction unsignedTransaction = BoxOperations.putToContractTxUnsigned(ctx,
                    Collections.singletonList(sender),
                    contract, amountToSend, Collections.emptyList());
            return ctx.newProverBuilder().build().reduce(unsignedTransaction, 0);
        });
    }

    private static boolean isMainNetAddress(String address) {
        try {
            return Address.create(address).isMainnet();
        } catch (Throwable t) {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
    }

    private static String getDefaultNodeUrl(boolean mainNet) {
        return mainNet ? NODE_MAINNET : NODE_TESTNET;
    }
}
