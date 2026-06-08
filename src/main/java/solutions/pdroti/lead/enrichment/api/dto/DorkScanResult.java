package solutions.pdroti.lead.enrichment.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Resultado de varredura inspirada em Google Dorks.
 * Detecta informações publicamente expostas que um dork encontraria.
 */
public record DorkScanResult(
        List<String> exposedEmails,
        List<String> exposedPhones,
        List<String> exposedAdminPaths,
        List<String> exposedDocuments,
        List<String> exposedConfigFiles,
        List<String> exposedBackupFiles,
        List<String> exposedErrorMessages,
        List<String> exposedLogFiles,
        List<String> exposedDatabaseInfo,
        Map<String, List<String>> serverHeaders,
        List<String> certificateEmails,
        int totalFindings
) {
    public static DorkScanResult empty() {
        return new DorkScanResult(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of(),
                List.of(), 0
        );
    }
}
