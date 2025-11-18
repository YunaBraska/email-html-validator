package berlin.yuna.ehv.validation;

/**
 * Tiny facade that forwards to {@link EmailHtmlValidatorRequest}. Think of it as
 * a shorthand dial so you don't have to type the whole class name every time.
 */
public final class EmailValidator {

    private EmailValidator() {
    }

    /**
     * Starts a validator request for the provided HTML snippet.
     *
     * @param html HTML fragment, file contents, or any string you'd pass to the CLI
     * @return fluent request builder (`EmailHtmlValidatorRequest`)
     */
    public static EmailHtmlValidatorRequest html(final String html) {
        return EmailHtmlValidatorRequest.html(html);
    }
}
