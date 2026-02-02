package survivor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author John Draa
 */

@Component
@ConfigurationProperties(prefix = "fog")
@Getter
@Setter
public class FogConfig
{
    private int light;
    private int wake;
    private int sleep;
}
