package kiit.codes;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static kiit.codes.Checks.collect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises kiit-codes from plain Java (not Kotlin). Guards the @JvmStatic/@JvmField/
 * @JvmOverloads/@JvmName annotations and the sealed-hierarchy `permits` metadata that make the
 * library usable idiomatically from Java, since nothing else in this repo compiles Java against it.
 */
public class JavaInteropTest {

    @Test
    public void companionConstantsAreDirectStaticFields() {
        // @JvmField: Passed.Succeeded.SUCCESS, not Passed.Succeeded.Companion.getSUCCESS()
        assertEquals("SUCCESS", Passed.Succeeded.SUCCESS.getName());
        assertEquals(StatusConstants.KIIT, Passed.Succeeded.SUCCESS.getOrigin());
        assertEquals("DENIED", Failed.Restricted.DENIED.getName());
    }

    @Test
    public void errCompanionFunctionsAreStaticWithOverloads() {
        // @JvmStatic + @JvmOverloads: Err.of("message"), no explicit null for the trailing Throwable
        Err err = Err.of("boom");
        assertEquals("boom", err.getMessage());
        assertEquals(null, err.getCause());
    }

    @Test
    public void codesToHttpHasNoArgConstructor() {
        // @JvmOverloads on the primary constructor
        CodesToHttp http = new CodesToHttp();
        assertEquals(200, http.toCode(Passed.Succeeded.SUCCESS));
        assertEquals(401, http.toCode(Failed.Restricted.UNAUTHENTICATED));
    }

    @Test
    public void checkedCompanionFunctionsAreStaticWithOverloads() {
        // @JvmStatic + @JvmOverloads: Checked.success(), no explicit Passed argument
        Checked ok = Checked.success();
        assertTrue(ok.isValid());

        List<Err> errors = Collections.singletonList(Err.of("bad request"));
        Checked failing = Checked.failure(Failed.Invalid.BAD_REQUEST, errors);
        assertFalse(failing.isValid());
    }

    @Test
    public void checksFacadeCollectsMultipleChecks() {
        // @file:JvmName("Checks") on Checked.kt: Java sees kiit.codes.Checks, not CheckedKt
        Checked ok = Checked.success();
        Checked failing = Checked.failure(Failed.Invalid.BAD_REQUEST, Collections.singletonList(Err.of("bad")));

        Checked combined = collect(ok, failing);
        assertFalse(combined.isValid());
        assertEquals(1, combined.getErrors().size());
    }

    @Test
    public void statusExceptionConstructorsHaveOverloads() {
        // @JvmOverloads: only the required Failed.Restricted argument, no explicit errors/cause
        StatusException.RestrictedException ex = new StatusException.RestrictedException(Failed.Restricted.DENIED);
        assertEquals(Failed.Restricted.DENIED, ex.getStatus());
    }

    @Test
    public void statusExceptionsFacadeConvertsFailedToException() {
        // @file:JvmName("StatusExceptions") on StatusException.kt, @JvmOverloads on toException
        StatusException ex = StatusExceptions.toException(Failed.Invalid.NOT_FOUND);
        assertTrue(ex instanceof StatusException.InvalidException);

        try {
            throw ex;
        } catch (StatusException caught) {
            assertEquals(Failed.Invalid.NOT_FOUND, caught.getStatus());
        }
    }

    @Test
    public void statusSealedHierarchySupportsExhaustiveSwitchPatternMatching() {
        // JDK 21 pattern-matching switch, exhaustive with no `default`. Only compiles if Kotlin
        // emitted PermittedSubclasses for Status/Passed/Failed (jvmTarget = JVM_21).
        assertEquals("Succeeded", groupOf(Passed.Succeeded.SUCCESS));
        assertEquals("Restricted", groupOf(Failed.Restricted.DENIED));
    }

    private static String groupOf(Status status) {
        return switch (status) {
            case Passed.Succeeded s -> "Succeeded";
            case Passed.Pending p -> "Pending";
            case Passed.Excluded e -> "Excluded";
            case Passed.Information i -> "Information";
            case Failed.Restricted r -> "Restricted";
            case Failed.Invalid i -> "Invalid";
            case Failed.Rejected r -> "Rejected";
            case Failed.Unserved u -> "Unserved";
        };
    }
}
