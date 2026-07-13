package com.portfolio.financas.transaction;

import com.portfolio.financas.common.InvalidRequestException;
import com.portfolio.financas.transaction.dto.ImportResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Upload de extrato bancario em CSV (colunas: data, descricao, valor).
 * O parsing tolerante a formato (separador ',' ou ';', datas dd/MM/yyyy
 * ou yyyy-MM-dd) e a deduplicacao contra transacoes ja existentes sao
 * responsabilidade de TransactionImportService (T-2.1.2); este controller
 * cuida apenas do transporte HTTP (multipart -> texto -> resultado JSON).
 */
@RestController
public class CsvImportController {

    private final TransactionImportService transactionImportService;

    public CsvImportController(TransactionImportService transactionImportService) {
        this.transactionImportService = transactionImportService;
    }

    @PostMapping(value = "/transactions/import", consumes = "multipart/form-data")
    public ResponseEntity<ImportResult> importTransactions(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Arquivo CSV ausente ou vazio.");
        }

        String content = readAsUtf8(file);
        ImportResult result = transactionImportService.importCsv(content, file.getOriginalFilename());
        return ResponseEntity.ok(result);
    }

    private String readAsUtf8(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado.", e);
        }
    }
}
