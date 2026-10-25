package net.yizuz.playerogrammod.ai;

/**
 * A failure that is safe to show to a player or server operator.
 */
public class AiException extends RuntimeException {
    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
