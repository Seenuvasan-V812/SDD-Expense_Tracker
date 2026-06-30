package com.dailyexpense.expense.dto;

import java.util.List;

public record ImportExpensesReport(
    int totalRows,
    int succeeded,
    int failed,
    int succeededWithWarning,
    List<ImportRowResult> results
) {
    public record ImportRowResult(int row, String status, String error, String warning) {}
}
