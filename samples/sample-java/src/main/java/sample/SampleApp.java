package sample;

import kiit.codes.Checked;
import kiit.codes.Checks;
import kiit.codes.CodesToHttp;
import kiit.codes.Err;
import kiit.codes.Failed;
import kiit.codes.Passed;
import kiit.codes.Status;
import kiit.codes.StatusException;
import kiit.codes.StatusExceptions;
import kiit.codes.formats.Catalog;
import kiit.codes.formats.CodeDetail;
import kiit.codes.formats.CodeDetails;
import kiit.codes.formats.CodesToProblem;
import kiit.codes.formats.ErrorDetail;
import kiit.codes.formats.ErrorItem;
import kiit.codes.formats.Problem;

import java.util.List;
import java.util.Map;

/**
 * Living documentation of kiit-codes from plain Java. Each section below only compiles because
 * of the @JvmStatic/@JvmField/@JvmOverloads/@file:JvmName annotations added to the library, and
 * the pattern-matching switch requires the sealed hierarchies to emit PermittedSubclasses
 * (jvmTarget = JVM_21). See kiit-codes/src/jvmTest/java/kiit/codes/JavaInteropTest.java for the
 * same surface exercised as assertions.
 */
public class SampleApp {

    public static void main(String[] args) {
        // @JvmField companion constants: Passed.Succeeded.SUCCESS, not .Companion.getSUCCESS()
        Status ok = Passed.Succeeded.SUCCESS;
        Status denied = Failed.Restricted.DENIED;
        System.out.println("ok status: " + describe(ok));
        System.out.println("denied status: " + describe(denied));

        // @JvmStatic + @JvmOverloads: Err.of(message), no explicit null for the trailing Throwable
        Err err = Err.of("email is required");
        System.out.println("err: " + err.getMessage());

        // @JvmOverloads on the primary constructor: no-arg CodesToHttp()
        CodesToHttp http = new CodesToHttp();
        System.out.println("http code for ok: " + http.toCode(ok));
        System.out.println("http code for denied: " + http.toCode(denied));

        // @JvmStatic + @JvmOverloads: Checked.success(), no explicit Passed argument
        Checked validEmail = Checked.success();
        Checked invalidEmail = Checked.failure(Failed.Invalid.BAD_REQUEST, List.of(err));
        System.out.println("validEmail.isValid = " + validEmail.isValid());
        System.out.println("invalidEmail.isValid = " + invalidEmail.isValid());

        // @file:JvmName("Checks"): kiit.codes.Checks, not CheckedKt
        Checked combined = Checks.collect(validEmail, invalidEmail);
        System.out.println("combined.isValid = " + combined.isValid() + ", errors = " + combined.getErrors().size());

        // @JvmOverloads on the StatusException subclasses: only the required status argument
        try {
            throw new StatusException.RestrictedException(Failed.Restricted.UNAUTHENTICATED);
        } catch (StatusException e) {
            System.out.println("caught: " + e.getStatus().getName() + " — " + e.getMessage());
        }

        // @file:JvmName("StatusExceptions") + @JvmOverloads: no explicit errors list needed
        StatusException fromStatus = StatusExceptions.toException(Failed.Invalid.NOT_FOUND);
        System.out.println("converted: " + fromStatus.getClass().getSimpleName());

        // Custom, consumer-defined Status: a non-kiit origin plus an internal-organization scope.
        // Failed.Rejected has no @JvmOverloads, so every constructor parameter must be supplied.
        Failed.Rejected duplicateCharge =
                new Failed.Rejected(
                        "DUPLICATE_CHARGE",
                        "This charge has already been processed",
                        "com.stripe",
                        "payments.cards");
        System.out.println("http code: " + http.toCode(duplicateCharge));

        // Catalog supplies baseUrl per origin; a built-in Status needs no registration, it
        // defaults to kiit-codes' own taxonomy docs.
        Catalog catalog = Catalog.of(Map.of("com.stripe", "https://stripe.com/problems"));
        CodesToProblem problems = new CodesToProblem(catalog, http);

        // Two independent converters off the same Status, pick whichever fits the boundary:

        // CodesToProblem.build (@JvmOverloads): the RFC 9457 shape, for an HTTP API response.
        Problem<ErrorDetail> stripeProblem = problems.build(duplicateCharge);
        System.out.println("[rfc]  type: " + stripeProblem.getType());
        System.out.println("[rfc]  title: " + stripeProblem.getTitle());
        System.out.println("[rfc]  status: " + stripeProblem.getStatus());

        // @file:JvmName("CodeDetails") + @JvmOverloads: kiit-codes' own shape, no baseUrl or HTTP
        // status needed. Useful for internal service-to-service calls and background jobs.
        CodeDetail<ErrorDetail> stripeCode = CodeDetails.toCodeDetail(duplicateCharge);
        System.out.println("[kiit] path: " + stripeCode.getPath());
        System.out.println("[kiit] code: " + stripeCode.getCode());
        System.out.println("[kiit] success: " + stripeCode.getSuccess());
        System.out.println("[kiit] message: " + stripeCode.getMessage());

        Problem<ErrorDetail> kiitProblem = problems.build(Failed.Invalid.NOT_FOUND);
        System.out.println("kiit problem type: " + kiitProblem.getType());

        // Custom error shape: supply your own ErrorItem when field + message isn't enough.
        Err.ErrorList validationErr =
                new Err.ErrorList(List.of(Err.on("phone", "1234567890123", "Too long")), "Validation failed");
        CodeDetail<DetailedError> customCode =
                CodeDetails.toCodeDetail(
                        Failed.Invalid.INVALID_VALUE,
                        validationErr,
                        e ->
                                new DetailedError(
                                        e instanceof Err.ErrorField f ? f.getField() : null,
                                        e.getMessage(),
                                        "check formatting"));
        System.out.println("[custom] code: " + customCode.getCode());
        System.out.println("[custom] errors: " + customCode.getErrors());
    }

    // Custom ErrorItem: field + message plus a hint, for when the default ErrorDetail shape
    // (field + message only) isn't enough.
    static final class DetailedError implements ErrorItem {
        private final String field;
        private final String message;
        private final String hint;

        DetailedError(String field, String message, String hint) {
            this.field = field;
            this.message = message;
            this.hint = hint;
        }

        @Override
        public String getField() {
            return field;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "DetailedError(field=" + field + ", message=" + message + ", hint=" + hint + ")";
        }
    }

    // JDK 21 pattern-matching switch, exhaustive with no `default` branch. Only compiles because
    // Status/Passed/Failed are sealed and Kotlin emitted PermittedSubclasses at jvmTarget 21.
    private static String describe(Status status) {
        return switch (status) {
            case Passed.Succeeded s -> "Succeeded: " + s.getName();
            case Passed.Pending p -> "Pending: " + p.getName();
            case Passed.Excluded e -> "Excluded: " + e.getName();
            case Passed.Information i -> "Information: " + i.getName();
            case Failed.Restricted r -> "Restricted: " + r.getName();
            case Failed.Invalid i -> "Invalid: " + i.getName();
            case Failed.Rejected r -> "Rejected: " + r.getName();
            case Failed.Unserved u -> "Unserved: " + u.getName();
        };
    }
}
