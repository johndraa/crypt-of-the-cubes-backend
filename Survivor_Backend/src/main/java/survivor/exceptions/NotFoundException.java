package survivor.exceptions;

/**
 * @author John Draa
 */

public class NotFoundException extends RuntimeException
{
    public NotFoundException(String message) { super(message); }
}