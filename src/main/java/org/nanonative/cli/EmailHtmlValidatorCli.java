package org.nanonative.cli;

import berlin.yuna.typemap.model.TypeMap;
import org.nanonative.validation.HtmlValidator;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;

public class EmailHtmlValidatorCli {

    private static final String OPTION_HELP = "help";
    private static final String OPTION_SOURCE = "source";
    private static final String OPTION_OUTPUT = "outputDir";

    private EmailHtmlValidatorCli() {
    }

    public static void main(final String[] args) {
        int status = execute(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int execute(final String[] args, final PrintStream out, final PrintStream err) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        try {
            var options = parseOptions(args);
            if (Boolean.TRUE.equals(options.asBooleanOpt(OPTION_HELP).orElse(false))) {
                printUsage(out);
                return 0;
            }

            var source = options.asStringOpt(OPTION_SOURCE)
                .orElseThrow(() -> new IllegalArgumentException("Missing HTML source. Provide inline HTML, '-', file path, or URL."));
            var html = HtmlSourceLoader.load(source);
            TypeMap report = HtmlValidator.validate(html);
            ReportExporter.printConsole(report, out);

            options.asStringOpt(OPTION_OUTPUT)
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .ifPresent(dir -> new ReportExporter(dir).export(report));
            return 0;
        } catch (IllegalArgumentException exception) {
            err.println("Input error: " + exception.getMessage());
            return 1;
        } catch (RuntimeException exception) {
            err.println("Unable to validate HTML: " + exception.getMessage());
            return 2;
        }
    }

    static TypeMap parseOptions(final String[] args) {
        var options = new TypeMap();
        options.put(OPTION_HELP, false);
        var builder = new StringBuilder();
        if (args != null) {
            for (int index = 0; index < args.length; index++) {
                var arg = args[index];
                if (arg == null) {
                    continue;
                }
                if ("-h".equals(arg) || "--help".equals(arg)) {
                    options.put(OPTION_HELP, true);
                    continue;
                }
                if ("-o".equals(arg) || "--output-dir".equals(arg)) {
                    if (index + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing path for " + arg);
                    }
                    var dir = args[++index];
                    if (dir == null || dir.isBlank()) {
                        throw new IllegalArgumentException("Output directory cannot be blank");
                    }
                    options.put(OPTION_OUTPUT, dir);
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(arg);
            }
        }
        if (!builder.isEmpty()) {
            options.put(OPTION_SOURCE, builder.toString());
        }
        return options;
    }

    private static void printUsage(final PrintStream out) {
        out.println("Email HTML Validator CLI");
        out.println("Usage:");
        out.println("  java -jar email-html-validator.jar [HTML|FILE|URL]");
        out.println("  cat template.html | java -jar email-html-validator.jar");
        out.println();
        out.println("Options:");
        out.println("  --help              Show this help message");
        out.println("  --output-dir <dir>  Persist JSON/XML/HTML/Markdown artifacts");
        out.println();
        out.println("Provide exactly one HTML source: inline HTML, a file path, an HTTP(S) URL, or pipe through stdin.");
    }
}
