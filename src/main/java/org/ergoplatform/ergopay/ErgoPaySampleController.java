package org.ergoplatform.ergopay;

import static org.ergoplatform.appkit.Parameters.MinFee;

import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.BoxOperations;
import org.ergoplatform.appkit.Eip4Token;
import org.ergoplatform.appkit.ErgoContract;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        return setAddress(sessionId, Collections.singletonList(address));
    }

    @PostMapping("/setAddress/{sessionId}/" + ErgoPayConstants.URL_CONST_MULTIPLE_ADDRESSES)
    public ErgoPayResponse setAddress(@PathVariable String sessionId, @RequestBody List<String> addresses) {
        UserData userData = sessionService.getUserData(sessionId);

        logger.info("Received addresses " + addresses.toString() + " for session " + sessionId);
        // we simply discard this here
        String address = addresses.get(0);

        ErgoPayResponse response = new ErgoPayResponse();

        // check the address
        try {
            boolean isMainNet = isMainNetAddress(address);

            response.address = address;
            response.message = "Connected to your address " + address + ".\n\nYou can now continue using the dApp.";
            response.messageSeverity = ErgoPayResponse.Severity.INFORMATION;

        } catch (Throwable t) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR;
            response.message = (t.getMessage());
        }

        return response;
    }

    @PostMapping("/setAddress/{sessionId}/" + ErgoPayConstants.URL_CONST_MULTIPLE_ADDRESSES_CHECK)
    public Object checkSetAddressMultiple(@PathVariable String sessionId) {
        // we support multiple addresses for this endpoint, so we return just 200
        return null;
    }

    @GetMapping("/roundTrip/{address}")
    public ErgoPayResponse roundTrip(@PathVariable String address,
                                     @RequestHeader Map<String, String> header) {
        return roundTrip(Collections.singletonList(address), header);
    }

    @PostMapping("/roundTrip/" + ErgoPayConstants.URL_CONST_MULTIPLE_ADDRESSES)
    public ErgoPayResponse roundTrip(@RequestBody List<String> addresses,
                                     @RequestHeader Map<String, String> header) {
        // sends 1 ERG around to and from the address

        ErgoPayResponse response = new ErgoPayResponse();

        try {
            boolean multipleAddressesSupported = ErgoPayConstants.HEADER_VALUE_SUPPORTED.equals(
                    header.get(ErgoPayConstants.HEADER_KEY_MULTIPLE_ADDRESSES.toLowerCase()));

            if (!multipleAddressesSupported && addresses.size() > 1)
                throw new IllegalArgumentException("Wallet App does not support multiple addresses to be selected");

            boolean isMainNet = isMainNetAddress(addresses.get(0));
            long amountToSend = 1000L * 1000L * 1000L;
            List<Address> senders = addresses.stream().map(Address::create).collect(Collectors.toList());
            Address recipient = Address.create(addresses.get(0));

            byte[] reduced = getReducedSendTx(isMainNet, amountToSend, senders, recipient).toBytes();

            response.reducedTx = Base64.getUrlEncoder().encodeToString(reduced);
            if (addresses.size() == 1)
                response.address = addresses.get(0);
            else
                response.addresses = addresses;
            response.message = "Here is your 1 ERG round trip.";
            response.messageSeverity = ErgoPayResponse.Severity.INFORMATION;

        } catch (Throwable t) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR;
            response.message = (t.getMessage());
            logger.error("Error round trip", t);
        }

        return response;
    }

    @PostMapping("/roundTrip/" + ErgoPayConstants.URL_CONST_MULTIPLE_ADDRESSES_CHECK)
    public Object checkRoundTripMultiple() {
        // we support multiple addresses for this endpoint, so we return just 200
        return null;
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
                        Eip4Token token = new Eip4Token(firstInputBoxId.toString(),
                                new BigDecimal(num).movePointRight(dec).longValueExact(),
                                name, "Minted with ErgoPay", dec);

                        ErgoContract contract = recipient.toErgoContract();

                        OutBoxBuilder outBoxBuilder = unsignedTxBuilder.outBoxBuilder()
                                .value(amountToSend)
                                .mintToken(token)
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
            logger.error("Error mint token", t);
        }

        return response;
    }

    @GetMapping("/burnToken/{address}")
    public ErgoPayResponse burnToken(@PathVariable String address,
                                     @RequestParam long num,
                                     @RequestParam String tokenId) {

        ErgoPayResponse response = new ErgoPayResponse();

        try {
            boolean isMainNet = isMainNetAddress(address);
            long amountToSend = 1000L * 1000L;
            Address sender = Address.create(address);
            Address recipient = Address.create(address);

            ErgoToken token = new ErgoToken(ErgoId.create(tokenId), num);

            byte[] reduced = getReducedTx(isMainNet, amountToSend, Collections.singletonList(token), sender,
                    unsignedTxBuilder -> {

                        ErgoContract contract = recipient.toErgoContract();

                        OutBoxBuilder outBoxBuilder = unsignedTxBuilder.outBoxBuilder()
                                .value(amountToSend)
                                .contract(contract);

                        OutBox newBox = outBoxBuilder.build();

                        unsignedTxBuilder.outputs(newBox).tokensToBurn(token);

                        return unsignedTxBuilder;
                    }
            ).toBytes();

            response.reducedTx = Base64.getUrlEncoder().encodeToString(reduced);
            response.address = address;
            response.message = "Send this transaction to burn the selected tokens (see below).";
            response.messageSeverity = ErgoPayResponse.Severity.INFORMATION;

        } catch (Throwable t) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR;
            response.message = (t.getMessage());
            logger.error("Error burn token", t);
        }

        return response;
    }

    private ReducedTransaction getReducedSendTx(boolean isMainNet, long amountToSend, List<Address> senders, Address recipient) {
        NetworkType networkType = isMainNet ? NetworkType.MAINNET : NetworkType.TESTNET;
        return RestApiErgoClient.create(
                getDefaultNodeUrl(isMainNet),
                networkType,
                "",
                RestApiErgoClient.getDefaultExplorerUrl(networkType)
        ).execute(ctx -> {
            ErgoContract contract = recipient.toErgoContract();
            UnsignedTransaction unsignedTransaction = BoxOperations.createForSenders(senders, ctx)
                    .withAmountToSpend(amountToSend)
                    .putToContractTxUnsigned(contract);
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

            List<InputBox> boxesToSpend = BoxOperations.createForSender(sender, ctx)
                    .withAmountToSpend(amountToSpend)
                    .withTokensToSpend(tokensToSpend)
                    .loadTop();

            P2PKAddress changeAddress = sender.asP2PK();
            UnsignedTransactionBuilder txB = ctx.newTxBuilder();

            UnsignedTransactionBuilder unsignedTransactionBuilder = txB.boxesToSpend(boxesToSpend)
                    .fee(MinFee)
                    .sendChangeTo(changeAddress);

            UnsignedTransaction unsignedTransaction = outputBuilder.apply(unsignedTransactionBuilder).build();

            return ctx.newProverBuilder().build().reduce(unsignedTransaction, 0);
        });
    }

    @GetMapping("/spendBox/{address}/{boxId}")
    public ErgoPayResponse burnToken(@PathVariable String address, @PathVariable String boxId) {

        ErgoPayResponse response = new ErgoPayResponse();

        try {
            boolean isMainNet = isMainNetAddress(address);
            NetworkType networkType = isMainNet ? NetworkType.MAINNET : NetworkType.TESTNET;
            Address recipient = Address.create(address);

            byte[] reduced = RestApiErgoClient.create(
                    getDefaultNodeUrl(isMainNet),
                    networkType,
                    "",
                    RestApiErgoClient.getDefaultExplorerUrl(networkType)
            ).execute(ctx -> {

                InputBox inputBox = ctx.getBoxesById(boxId)[0];

                UnsignedTransactionBuilder txB = ctx.newTxBuilder();

                OutBox newBox = txB.outBoxBuilder()
                        .value(inputBox.getValue() - MinFee)
                        .tokens(inputBox.getTokens().toArray(new ErgoToken[0]))
                        .contract(recipient.toErgoContract()).build();

                UnsignedTransaction unsignedTransaction = txB.boxesToSpend(Collections.singletonList(inputBox))
                        .fee(MinFee)
                        .outputs(newBox)
                        .sendChangeTo(recipient.asP2PK())
                        .build();

                return ctx.newProverBuilder().build().reduce(unsignedTransaction, 0);
            }).toBytes();

            response.reducedTx = Base64.getUrlEncoder().encodeToString(reduced);
            response.address = address;
            response.message = "Send this transaction to burn the selected tokens (see below).";
            response.messageSeverity = ErgoPayResponse.Severity.INFORMATION;

        } catch (Throwable t) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR;
            response.message = (t.getMessage());
            logger.error("Error spend box", t);
        }

        return response;
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
