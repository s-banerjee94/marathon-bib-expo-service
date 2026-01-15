package com.timekeeper.bibexpo.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvParseResult {

    @Builder.Default
    private List<CsvRow> rows = new ArrayList<>();

    @Builder.Default
    private List<String> goodiesColumns = new ArrayList<>();

    private int totalRows;
}
