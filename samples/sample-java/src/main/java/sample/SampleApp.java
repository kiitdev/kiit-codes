package sample;

import kiit.codes.Checked;
import kiit.codes.Checks;
import kiit.codes.CodeDetail;
import kiit.codes.CodeDetails;
import kiit.codes.CodesToHttp;
import kiit.codes.Err;
import kiit.codes.Failed;
import kiit.codes.Passed;
import kiit.codes.ProblemDetail;
import kiit.codes.Problems;
import kiit.codes.Status;
import kiit.codes.StatusException;
import kiit.codes.StatusExceptions;

import java.util.List;

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

        // Two independent converters off the same Status, pick whichever fits the boundary:

        // @file:JvmName("Problems") + @JvmOverloads: the RFC 9457 shape, for an HTTP API response.
        // baseUrl is required here since origin isn't kiit-codes' own ("dev.kiit").
        ProblemDetail stripeProblem = Problems.toProblemDetail(duplicateCharge, null, "https://stripe.com/problems");
        System.out.println("[rfc]  type: " + stripeProblem.getType());
        System.out.println("[rfc]  title: " + stripeProblem.getTitle());
        System.out.println("[rfc]  status: " + stripeProblem.getStatus());

        // @file:JvmName("CodeDetails") + @JvmOverloads: kiit-codes' own shape, no baseUrl or HTTP
        // status needed. Useful for internal service-to-service calls and background jobs.
        CodeDetail stripeCode = CodeDetails.toCodeDetail(duplicateCharge, null);
        System.out.println("[kiit] path: " + stripeCode.getPath());
        System.out.println("[kiit] code: " + stripeCode.getCode());
        System.out.println("[kiit] success: " + stripeCode.getSuccess());
        System.out.println("[kiit] message: " + stripeCode.getMessage());

        // A built-in Status needs no baseUrl, it defaults to kiit-codes' own https://kiit.dev/problems.
        ProblemDetail kiitProblem = Problems.toProblemDetail(Failed.Invalid.NOT_FOUND, null, null);
        System.out.println("kiit problem type: " + kiitProblem.getType());
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
