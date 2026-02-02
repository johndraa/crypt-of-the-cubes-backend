package survivor.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer
{
    @Override public void configureMessageBroker(MessageBrokerRegistry cfg)
    {
        cfg.enableSimpleBroker("/topic", "/queue");  // broker destinations
        cfg.setUserDestinationPrefix("/user");       // for per-user queues
        cfg.setApplicationDestinationPrefixes("/app"); // for @MessageMapping
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry reg)
    {
        reg.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }
}
