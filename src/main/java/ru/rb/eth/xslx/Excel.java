package ru.rb.eth.xslx;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rb.eth.model.Transaction;
import ru.rb.eth.model.TransactionStatus;
import ru.rb.eth.util.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Excel {

    private ArrayDeque<Transaction> transactions = new ArrayDeque<>();

    private Path bookPath;

    private String bookName;
    private Path progressDirPath;

    private final XSSFWorkbook workbook;
    private XSSFSheet sheet;

    private BigInteger totalAmount = BigInteger.ZERO;
    private boolean hasErrors = false;

    private BigDecimal e18;

    private Logger log;

    public Excel(String path) throws IOException {
        log = LoggerFactory.getLogger(Excel.class);

        e18 = new BigDecimal("1000000000000000000");

        bookPath = Paths.get(path).normalize().toAbsolutePath();
        bookName = bookPath.getFileName().toString().substring(0, bookPath.getFileName().toString().lastIndexOf("."));

        workbook = new XSSFWorkbook(Files.newInputStream(bookPath));
        sheet = workbook.getSheetAt(0);

        XSSFCellStyle alignCenterStyle = workbook.createCellStyle();
        alignCenterStyle.setAlignment(HorizontalAlignment.CENTER);

        String[] titles = new String[]{"Status", "Gas used", "Tx hash", "Error"};
        for(int i = 2; i < 6; i++) {
            XSSFCell tempCell = sheet.getRow(0).createCell(i);
            tempCell.setCellValue(titles[i-2]);
            tempCell.setCellStyle(alignCenterStyle);
        }
        log.info("Book is read");
    }

    public ArrayDeque<Transaction> getDeque() {
        return transactions;
    }

    public String getBookName() {
        return bookName;
    }

    public Path getProgressDirPath() {
        return progressDirPath;
    }

    public BigInteger getTotalAmount() {
        return totalAmount;
    }

    public boolean isHasErrors() {
        return hasErrors;
    }

    public List<Transaction> parseSheet() {
        log.info("Start parsing. Total row count: " + sheet.getLastRowNum());
        XSSFCellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);

        List<Transaction> parsingErrTxs = new ArrayList<>();
        DataFormatter df = new DataFormatter();

        for(int i = 1; i <= sheet.getLastRowNum(); i++) {
            XSSFRow row = sheet.getRow(i);

            String amountStr = df.formatCellValue(row.getCell(1));
            String addressStr = df.formatCellValue(row.getCell(0));

            if (!Util.isValidAddress(addressStr)) {
                log.warn("Error parsing address in line " + i + "! Skip this row!");
                Transaction tx = new Transaction(-1, addressStr, amountStr);
                tx.updateStatus(TransactionStatus.ADDRESS_PARSING_ERROR);
                parsingErrTxs.add(tx);
                parsingError(0, row);
                continue;
            }

            if(!Util.isValidAmount(amountStr)) {
                log.warn("Error parsing amount in line " + i + "! Skip this row!");
                Transaction tx = new Transaction(-1, addressStr, amountStr);
                tx.updateStatus(TransactionStatus.AMOUNT_PARSING_ERROR);
                parsingErrTxs.add(tx);
                parsingError(1, row);
                continue;
            }

            Transaction transaction = new Transaction(i, addressStr, amountStr);
            transactions.add(transaction);
            totalAmount = totalAmount.add(new BigDecimal(amountStr).multiply(e18).toBigIntegerExact());

            XSSFCell statusCell = row.createCell(2);
            statusCell.setCellValue("PARSED");
            statusCell.setCellStyle(cellStyle);
        }

        log.info("Parsed rows: " + transactions.size());
        writeBook(true);
        return parsingErrTxs;
    }

    private void parsingError(int errColumn, XSSFRow row) {
        hasErrors = true;
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        row.getCell(errColumn).setCellStyle(style);

        style.setAlignment(HorizontalAlignment.CENTER);
        XSSFCell statusCell = row.createCell(2);
        statusCell.setCellValue("PARSING ERROR");
        statusCell.setCellStyle(style);
        sheet.autoSizeColumn(2);
    }

    public void writeBook(boolean parsingResult) {
        synchronized (workbook) {
            try {
                String timeStamp = new SimpleDateFormat("dd-MM-HH.mm.ss").format(new Date());
                progressDirPath = Paths.get(bookPath.getParent().toString(), bookName + "_progress");
                if(!Files.exists(progressDirPath)) {
                    Files.createDirectory(progressDirPath);
                }

                Path workBookPath;
                if(parsingResult) {
                    workBookPath = Paths.get(progressDirPath.toString(), bookName + "_parsing_" + timeStamp + ".xlsx");
                } else {
                    workBookPath = Paths.get(progressDirPath.toString(), bookName + "_" + timeStamp + ".xlsx");
                }
                try (OutputStream outputStream = Files.newOutputStream(workBookPath)){
                    workbook.write(outputStream);
                }

                log.info("Progress written to file " + workBookPath);
            } catch (IOException e) {
                log.error("", e);
            }
        }
    }

    public void closeBook() {
        try {
            workbook.close();
        } catch (IOException e) {
            log.error("", e);
        }
    }

    public void updateStatus(Transaction transaction) {
        synchronized (workbook) {
            XSSFRow row = sheet.getRow(transaction.getRowIndex());
            XSSFCell cell = row.getCell(2);

            XSSFCellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cellStyle.setAlignment(HorizontalAlignment.CENTER);
            switch (transaction.getStatus()) {
                case TransactionStatus.PENDING:
                    cellStyle.setFillForegroundColor(IndexedColors.DARK_YELLOW.getIndex());
                    cell.setCellValue("PENDING");
                    break;
                case TransactionStatus.CONFIRMED:
                    cellStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
                    cell.setCellValue("CONFIRMED");

                    row.createCell(3).setCellValue(transaction.getGasUsed().toString(10));
                    sheet.autoSizeColumn(3);

                    row.createCell(4).setCellValue(transaction.getHash());
                    sheet.autoSizeColumn(4);
                    break;
                case TransactionStatus.ERROR:
                    cellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
                    cell.setCellValue("ERROR");

                    row.createCell(5).setCellValue(transaction.getErrorMsg());
                    sheet.autoSizeColumn(5);

                    if(transaction.getHash() != null) {
                        row.createCell(4).setCellValue(transaction.getHash());
                        sheet.autoSizeColumn(4);
                    }
                    break;
                default:
                    break;
            }
            cell.setCellStyle(cellStyle);
        }
    }

    public void setTotal(String totalGas, String totalEth, String totalUsd) {
        sheet.getRow(0).createCell(6).setCellValue("Total gas: " + totalGas);
        sheet.getRow(1).createCell(6).setCellValue("Total ETH: " + totalEth);
        sheet.getRow(2).createCell(6).setCellValue("Total USD: " + totalUsd);
        sheet.autoSizeColumn(6);
    }
}
