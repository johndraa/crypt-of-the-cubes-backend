package survivor.chat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * @author John Draa
 * Required to let Spring boot up @ServerEndpoint beans.
 * (Only needed when NOT deploying to a standalone servlet container.)
 * Excluded from test profile to avoid ServerContainer requirement in test context.
 */
@Configuration
@ConditionalOnWebApplication
@Profile("!test")
public class ClassChatConfig
{
    @Bean
    public ServerEndpointExporter serverEndpointExporter()
    {
        return new ServerEndpointExporter();
    }
}
