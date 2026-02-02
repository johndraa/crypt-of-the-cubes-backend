package survivor.progress;

import lombok.Data;

/**
 * @author John Draa
 */

@Data
public class ProgressUpdateDTO
{
    @jakarta.validation.constraints.Min(value = 0, message = "coins must be >= 0")
    private Integer coins;

    @jakarta.validation.constraints.Min(value = 0, message = "totalScore must be >= 0")
    private Integer totalScore;
}
