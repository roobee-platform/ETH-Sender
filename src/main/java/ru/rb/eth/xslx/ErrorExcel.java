package ru.rb.eth.xslx;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rb.eth.model.Transaction;
import ru.rb.eth.model.TransactionStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ErrorExcel {

    private Logger log;

    private XSSFWorkbook workbook;
    private XSSFSheet sheet;

    public ErrorExcel() {
        log = LoggerFactory.getLogger(ErrorExcel.class);

        workbook = new XSSFWorkbook();
        sheet = workbook.createSheet();
        XSSFRow row = sheet.createRow(0);
        row.createCell(0).setCellValue("Address");
        row.createCell(1).setCellValue("Amount");
    }

    public void add(List<Transaction> txs) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for(int i = 0; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            XSSFRow row = sheet.createRow(i+1);
            XSSFCell cell0 = row.createCell(0);
            if(tx.getStatus() == TransactionStatus.ADDRESS_PARSING_ERROR) {
                cell0.setCellStyle(style);
            }
            cell0.setCellValue(tx.getTo());

            XSSFCell cell1 = row.createCell(1);
            if(tx.getStatus() == TransactionStatus.AMOUNT_PARSING_ERROR) {
                cell1.setCellStyle(style);
            }
            cell1.setCellValue(tx.getAmount());

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
        }
    }

    public void writeBook(Path dirPath, String name) {
        String timeStamp = new SimpleDateFormat("dd-MM-HH.mm.ss").format(new Date());
        Path errPath = Paths.get(dirPath.toString(), name + "_err_" + timeStamp + ".xlsx");
        try (OutputStream outputStream = Files.newOutputStream(errPath)) {
            workbook.write(outputStream);
        } catch (IOException e) {
            log.error("", e);
        }
    }

    public void closeBook() {
        try {
            workbook.close();
        } catch (IOException e) {
            log.error("", e);
        }
    }
}
