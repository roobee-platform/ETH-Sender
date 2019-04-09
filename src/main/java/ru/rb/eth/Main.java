package ru.rb.eth;

import com.beust.jcommander.JCommander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.tx.FastRawTransactionManager;
import ru.rb.eth.contract.TestToken;
import ru.rb.eth.model.Transaction;
import ru.rb.eth.model.TransactionStatus;
import ru.rb.eth.util.Args;
import ru.rb.eth.util.Util;
import ru.rb.eth.xslx.ErrorExcel;
import ru.rb.eth.xslx.Excel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public static void main(String[] argv) {

        Args args = new Args();
        JCommander.newBuilder()
                .addObject(args)
                .build()
                .parse(argv);

        new Main().start(args);
    }

    private Logger log;

    private Excel excel;
    private List<Transaction> errorTxs = new ArrayList<>();

    private int txCount;
    private int pendingCount;
    private AtomicInteger performedCount = new AtomicInteger();
    private AtomicInteger errorCount = new AtomicInteger();

    private BigDecimal gasPriceEth;
    private AtomicLong totalGas = new AtomicLong();

    private Timer dequeTimer;
    private Timer progressTimer;

    private void start(Args args) {
        log = LoggerFactory.getLogger(Main.class);

        try {

            excel = new Excel(args.getXlsxPath());
            errorTxs.addAll(excel.parseSheet());

            if(excel.isHasErrors()) {
                log.warn("Errors occurred while parsing!");
                YNdialog();
            }

            ArrayDeque<Transaction> transactions = excel.getDeque();
            txCount = transactions.size();

            Web3j web3 = Web3j.build(new HttpService(args.getUrl()));
            Credentials credentials = Credentials.create(args.getPrivateKey());

            log.info("Credentials loaded");
            FastRawTransactionManager fastRawTransactionManager = new FastRawTransactionManager(web3, credentials);

            BigInteger gasPrice = web3.ethGasPrice().send().getGasPrice();
            BigDecimal e18 = new BigDecimal("1000000000000000000");
            gasPriceEth = new BigDecimal(gasPrice).divide(e18, 18, RoundingMode.UNNECESSARY);

            TestToken contract = TestToken.load(
                    args.getContractAdress(),
                    web3, fastRawTransactionManager,
                    gasPrice,
                    Contract.GAS_LIMIT);

            BigInteger balance = contract.balanceOf(credentials.getAddress()).send();
            if(balance.compareTo(excel.getTotalAmount()) < 0) {
                log.warn("Insufficient funds!");
                log.warn("Your balance: " + balance);
                log.warn("Total tokens to send: " + excel.getTotalAmount());
                YNdialog();
            }

            BigDecimal dec = new BigDecimal("1" + String.join("", Collections.nCopies(contract.decimals().send().intValue(), "0")));

            dequeTimer = new Timer();
            TimerTask dequeTask = new TimerTask() {
                @Override
                public void run() {
                    if(transactions.size() == 0) {
                        dequeTimer.cancel();
                        dequeTimer.purge();
                    } else {
                        Transaction transaction = transactions.pop();
                        contract.transfer(transaction.getTo(), transaction.getAmountDec().multiply(dec).toBigIntegerExact())
                                .sendAsync()
                                .thenAccept(transactionReceipt -> handleTransaction(transaction, transactionReceipt))
                                .exceptionally(throwable -> handleException(transaction, throwable));

                        transaction.updateStatus(TransactionStatus.PENDING);
                        excel.updateStatus(transaction);

                        if (++pendingCount % 10 == 0) {
                            log.info("Tx sent: " + pendingCount + "/" + txCount);
                        }
                    }
                }
            };
            dequeTimer.schedule(dequeTask, 0L, args.getTxTime());

            progressTimer = new Timer();
            TimerTask progressTask = new TimerTask() {
                @Override
                public void run() {
                    excel.writeBook(false);
                }
            };
            long totalDelay = args.getTime() * 1000L;
            progressTimer.schedule(progressTask, totalDelay, totalDelay);

        } catch (Exception e) {
            log.error("", e);
        }
    }

    private void handleTransaction(Transaction transaction, TransactionReceipt transactionReceipt) {
        transaction.setHash(transactionReceipt.getTransactionHash());
        if(transactionReceipt.getStatus().equals("0x0")) {
            handleException(transaction, new Exception("Token Transfer Error (Token contract is locked or sender has an insufficient token balance)"));
        } else {
            transaction.updateStatus(TransactionStatus.CONFIRMED);
            transaction.setGasUsed(transactionReceipt.getGasUsed());
            totalGas.addAndGet(transactionReceipt.getGasUsed().longValueExact());
            excel.updateStatus(transaction);

            checkFinished(performedCount.incrementAndGet());
        }
    }

    private Void handleException(Transaction transaction, Throwable throwable) {
        errorTxs.add(transaction);
        transaction.updateStatus(TransactionStatus.ERROR);
        transaction.setErrorMsg(throwable.getMessage());
        excel.updateStatus(transaction);

        errorCount.incrementAndGet();
        checkFinished(performedCount.incrementAndGet());

        return null;
    }

    private void checkFinished(int count) {
        if(count == txCount) {
            progressTimer.cancel();
            progressTimer.purge();

            log.info("Processed transactions: " + txCount + " | Errors: " + errorCount);

            BigDecimal totalEth = gasPriceEth.multiply(new BigDecimal(totalGas.get()));
            double usdPrice = Util.getEthPrice();
            String totalGasStr = String.valueOf(totalGas);
            String totalEthStr = totalEth.stripTrailingZeros().toPlainString();
            String totalUsdStr = "";
            if(!Double.isNaN(usdPrice)) {
                totalUsdStr = totalEth.multiply(new BigDecimal(usdPrice)).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
            }

            log.info("Total gas: " + totalGasStr);
            log.info("Total ETH: " + totalEthStr);
            log.info("Total USD: " + totalUsdStr);

            excel.setTotal(totalGasStr, totalEthStr, totalUsdStr);

            excel.writeBook(false);
            excel.closeBook();

            if(errorTxs.size() != 0) {
                writeErrorTxs();
            }
        }
    }

    private void writeErrorTxs() {
        ErrorExcel errorExcel = new ErrorExcel();
        errorExcel.add(errorTxs);
        errorExcel.writeBook(excel.getProgressDirPath(), excel.getBookName());
        errorExcel.closeBook();
    }

    private void YNdialog() throws IOException {
        log.warn("Do you want to continue? Enter \"Y\" to contunue or \"N\" to exit");
        BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
        String input;
        do {
            input = bufferRead.readLine().toUpperCase();
        } while (!input.equals("Y") && !input.equals("N"));
        if(input.equals("N")) {
            System.exit(0);
        }
    }
}
