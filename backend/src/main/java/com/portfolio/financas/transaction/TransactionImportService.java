package com.portfolio.financas.transaction;

import com.portfolio.financas.transaction.dto.ImportResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persiste o resultado do parsing de CSV (CsvTransactionParser) com
 * deduplicacao: uma linha valida cuja combinacao data+descricao+valor ja
 * existe -- no banco ou em outra linha do mesmo arquivo -- e ignorada e
 * contada separadamente, em vez de gerar uma nova transacao ou violar a
 * constraint UNIQUE de hash_deduplicacao (V1__init.sql).
 */
@Service
public class TransactionImportService {

    private final TransactionRepository transactionRepository;

    public TransactionImportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public ImportResult importCsv(String csvContent, String originFilename) {
        CsvTransactionParser.ParseOutcome outcome = CsvTransactionParser.parse(csvContent);
        return persist(outcome, originFilename);
    }

    private ImportResult persist(CsvTransactionParser.ParseOutcome outcome, String originFilename) {
        List<ParsedTransactionRow> rows = outcome.validRows();

        Set<String> hashesInBatch = new HashSet<>();
        for (ParsedTransactionRow row : rows) {
            hashesInBatch.add(DeduplicationHash.compute(row.data(), row.descricao(), row.valor()));
        }
        Set<String> existingHashes = transactionRepository.findExistingHashes(hashesInBatch);

        Set<String> seenInThisImport = new HashSet<>();
        List<Transaction> toPersist = new ArrayList<>();
        int ignoradasDuplicadas = 0;

        for (ParsedTransactionRow row : rows) {
            String hash = DeduplicationHash.compute(row.data(), row.descricao(), row.valor());
            // Duplicada se ja existe no banco OU se ja apareceu antes neste
            // mesmo arquivo (ex: extrato com a mesma linha repetida).
            boolean isDuplicate = existingHashes.contains(hash) || !seenInThisImport.add(hash);
            if (isDuplicate) {
                ignoradasDuplicadas++;
                continue;
            }
            toPersist.add(new Transaction(row.data(), row.descricao(), row.valor(), originFilename, hash));
        }

        transactionRepository.saveAll(toPersist);

        return new ImportResult(toPersist.size(), ignoradasDuplicadas, outcome.invalidRows());
    }
}
