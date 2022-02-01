package org.ergoplatform.ergopay;

import static org.ergoplatform.appkit.Parameters.MinFee;

import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.BoxOperations;
import org.ergoplatform.appkit.ErgoId;
import org.ergoplatform.appkit.ErgoToken;
import org.ergoplatform.appkit.InputBox;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.appkit.OutBox;
import org.ergoplatform.appkit.OutBoxBuilder;
import org.ergoplatform.appkit.ReducedTransaction;
import org.ergoplatform.appkit.RestApiErgoClient;
import org.ergoplatform.appkit.UnsignedTransaction;
import org.ergoplatform.appkit.UnsignedTransactionBuilder;
import org.ergoplatform.appkit.impl.ErgoTreeContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@RestController
@CrossOrigin
public class ErgoPaySampleController {
    // this class processes all requests from the an ErgoPay wallet application

    public static final String NODE_MAINNET = "http://213.239.193.208:9053/";
    public static final String NODE_TESTNET = "http://213.239.193.208:9052/";

    private final UserSessionService sessionService;
    private final Logger logger = LoggerFactory.getLogger(ErgoPaySampleController.class);

    public ErgoPaySampleController(UserSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/setAddress/{sessionId}/{address}")
    public ErgoPayResponse setAddress(@PathVariable String sessionId, @PathVariable String address) {
        UserData userData = sessionService.getUserData(sessionId);

        logger.info("Received address " + address + " for session " + sessionId);

        ErgoPayResponse response = new ErgoPayResponse();

        // check the address
        try {
            boolean isMainNet = isMainNetAddress(address);

            response.address = address;
            userData.p2pkAddress = address;
            response.message = "Connected to your address " + address + ".\n\nYou can now continue using the dApp.";
            response.messageSeverity = ErgoPayResponse.Severity.INFORMATION;

        } catch (Throwable t) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR;
            response.message = (t.getMessage());
        }

        return response;
    }

    @GetMapping("/roundTrip/{address}")
    public ErgoPayResponse roundTrip(@PathVariable String address) {
        // sends 1 ERG around to and from the address

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

    @PostMapping("/reply/{sessionId}")
    public void postReply(@PathVariable String sessionId, @RequestBody ErgoPayReply reply) {
        // get a reply from the wallet app when a transaction was uploaded to a node.
        // You can use this to set the tx id to the user session and continue your payment process

        logger.info(reply.txId);
    }

    @GetMapping("/mintToken/{address}")
    public ErgoPayResponse mintToken(@PathVariable String address,
                                     @RequestParam(defaultValue = "1") long num,
                                     @RequestParam(defaultValue = "0") int dec,
                                     @RequestParam(required = true) String name) {

        ErgoPayResponse response = new ErgoPayResponse();

        try {
            boolean isMainNet = isMainNetAddress(address);
            long amountToSend = 1000L * 1000L;
            Address sender = Address.create(address);
            Address recipient = Address.create(address);

            byte[] reduced = getReducedTx(isMainNet, amountToSend, Collections.emptyList(), sender,
                    unsignedTxBuilder -> {

                        ErgoId firstInputBoxId = unsignedTxBuilder.getInputBoxes().get(0).getId();
                        ErgoToken token = new ErgoToken(firstInputBoxId,
                                new BigDecimal(num).movePointRight(dec).longValueExact());

                        ErgoTreeContract contract = new ErgoTreeContract(recipient.getErgoAddress().script());

                        OutBoxBuilder outBoxBuilder = unsignedTxBuilder.outBoxBuilder()
                                .value(amountToSend)
                                .mintToken(token, name, "Minted with ErgoPay", dec)
                                .contract(contract);

                        OutBox newBox = outBoxBuilder.build();

                        unsignedTxBuilder.outputs(newBox);

                        return unsignedTxBuilder;
                    }
            ).toBytes();

            response.reducedTx = Base64.getUrlEncoder().encodeToString(reduced);
            response.address = address;
            response.message = "Send this transaction to mint " + num + " tokens named '" + name + "'";
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

    private ReducedTransaction getReducedTx(boolean isMainNet, long amountToSpend, List<ErgoToken> tokensToSpend,
                                            Address sender,
                                            Function<UnsignedTransactionBuilder, UnsignedTransactionBuilder> outputBuilder) {
        NetworkType networkType = isMainNet ? NetworkType.MAINNET : NetworkType.TESTNET;
        return RestApiErgoClient.create(
                getDefaultNodeUrl(isMainNet),
                networkType,
                "",
                RestApiErgoClient.getDefaultExplorerUrl(networkType)
        ).execute(ctx -> {

            List<InputBox> boxesToSpend = BoxOperations.loadTop(ctx, sender, amountToSpend + MinFee, tokensToSpend);

            P2PKAddress changeAddress = sender.asP2PK();
            UnsignedTransactionBuilder txB = ctx.newTxBuilder();

            UnsignedTransactionBuilder unsignedTransactionBuilder = txB.boxesToSpend(boxesToSpend)
                    .fee(MinFee)
                    .sendChangeTo(changeAddress);

            UnsignedTransaction unsignedTransaction = outputBuilder.apply(unsignedTransactionBuilder).build();

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
