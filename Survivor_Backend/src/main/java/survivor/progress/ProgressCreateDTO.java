package survivor.progress;

import jakarta.validation.constraints.NotNull;

/**
 * @author John Draa
 * @param accountId
 */

public record ProgressCreateDTO(
        @NotNull(message = "accountId is required")
        Integer accountId
) {}
