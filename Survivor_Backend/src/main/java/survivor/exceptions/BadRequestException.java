package survivor.exceptions;

/**
 * @author John Draa
 */

public class BadRequestException extends RuntimeException
{
    public BadRequestException(String message) { super(message); }
}