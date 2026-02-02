package survivor.ws.dto;

/**
 * @author John Draa
 */

public record ReadyMsg(long matchId,int accountId,boolean ready){}
