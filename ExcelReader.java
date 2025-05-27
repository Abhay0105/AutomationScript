package com.qa.nal.utils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.*;

public class ExcelReader {

    public static List<String> readQueriesFromExcel(String filePath, String sheetName) {
        List<String> queries = new ArrayList<>();
        try (FileInputStream file = new FileInputStream(new File(filePath))) {
            Workbook workbook = WorkbookFactory.create(file);
            Sheet sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                throw new RuntimeException("Sheet '" + sheetName + "' not found.");
            }

            // Start from Row 1 (skip header row at index 0)
            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row != null) {
                    Cell queryCell = row.getCell(1); // Column B (Query)
                    if (queryCell != null) {
                        String query = queryCell.getCellType() == CellType.STRING
                            ? queryCell.getStringCellValue().trim()
                            : String.valueOf(queryCell.getNumericCellValue()).trim();
                        if (!query.isEmpty()) {
                            queries.add(query);
                        }
                    }
                }
            }
            System.out.println("Queries loaded from Excel: " + queries); // Debug log
        } catch (Exception e) {
            throw new RuntimeException("Error reading Excel: " + e.getMessage(), e);
        }
        return queries;
    }
}