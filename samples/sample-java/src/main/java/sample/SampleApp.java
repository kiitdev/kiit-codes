package sample;

/*
<example id="setup-install" tags="setup">
```xml title="pom.xml"
<dependency>
    <groupId>{{module.group}}</groupId>
    <artifactId>{{module.artifact}}</artifactId>
    <version>{{module.version}}</version>
</dependency>
```
```groovy title="build.gradle"
dependencies {
    implementation '{{module.group}}:{{module.artifact}}:{{module.version}}'
}
```
</example>
*/

/*
<example id="setup-imports" tags="setup">
```java
import kiit.codes.*;
import kiit.codes.formats.*;
```
</example>
*/

import kiit.codes.Checked;
import kiit.codes.Checks;
import kiit.codes.CodesToGrpc;
import kiit.codes.CodesToHttp;
import kiit.codes.CompositeLookup;
import kiit.codes.Err;
import kiit.codes.Failed;
import kiit.codes.Passed;
import kiit.codes.Status;
import kiit.codes.StatusException;
import kiit.codes.StatusExceptions;
import kiit.codes.formats.CodeDetail;
import kiit.codes.formats.CodeDetails;
import kiit.codes.formats.ErrorDetail;
import kiit.codes.formats.Problem;
import kiit.codes.formats.ProblemConverter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Canonical sample for kiit-codes from plain Java. It holds the code blocks the docs show for Java: the Tutorial
 * steps and the Guide recipes.
 *
 * 1. Every example is wrapped in `// <example id="..." tags="...">` ... `// </example>`, with the same ids as the
 *    Kotlin sample, so one `<Example id="..." />` shows a tab per language.
 * 2. The `check(...)` calls sit outside the markers, they check the example still works.
 * 3. Java has no local functions, so a recipe that needs one uses a lambda.
 * 4. The Java sample has no JSON recipe, it has no JSON library. The pattern-matching switch needs JDK 21.
 * 5. Run it with `./gradlew :samples:sample-java:run`.
 */
public class SampleApp {

    private static int checks = 0;

    /** Fails the run if a claim an example makes stops being true. Outside the example markers on purpose. */
    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new IllegalStateException("FAILED: " + label);
        }
        checks++;
        System.out.println("  ok: " + label);
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println(title);
        System.out.println("=".repeat(60));
    }

    public static void main(String[] args) {
        tutorialStatus();
        tutorialValidate();
        guideBuiltins();
        guideCustomCode();
        guidePatternMatching();
        guideCollectErrors();
        guideErrorDetails();
        guideExceptions();
        guideTypeUrl();
        guideCustomTypeUrl();
        guideHttp();
        guideCustomProtocol();

        System.out.println();
        System.out.println("All " + checks + " checks passed.");
    }

    // ============================================================
    // Tutorial: three short steps, each one a quick win
    // ============================================================

    private static void tutorialStatus() {
        section("Tutorial 1: Return a status");

        // <example id="tutorial-status" tags="tutorial">
        // A failure you expect is a value, not an exception
        Function<String, Status> validate = title -> {
            if (title.isBlank()) {
                return Failed.Invalid.INVALID_VALUE;
            }
            return Passed.Succeeded.SUCCESS;
        };

        Status good = validate.apply("buy milk");
        // success = true, group = Succeeded, name = SUCCESS
        System.out.println("success = " + good.getSuccess() + ", group = " + good.getGroup() + ", name = " + good.getName());

        Status bad = validate.apply("");
        // success = false, group = Invalid, name = INVALID_VALUE
        System.out.println("success = " + bad.getSuccess() + ", group = " + bad.getGroup() + ", name = " + bad.getName());
        // </example>
        check(good == Passed.Succeeded.SUCCESS && good.getSuccess() && good.getGroup().equals("Succeeded"), "tutorial-status: passes");
        check(bad == Failed.Invalid.INVALID_VALUE && !bad.getSuccess() && bad.getGroup().equals("Invalid"), "tutorial-status: fails");
    }

    private static void tutorialValidate() {
        section("Tutorial 2 and 3: Validate, then build a problem");

        // <example id="tutorial-validate" tags="tutorial">
        // Checked carries the status and the errors together
        Function<String, Checked> validate = title -> {
            if (!title.isBlank()) {
                return Checked.success();
            }
            return Checked.failure(Failed.Invalid.INVALID_VALUE, List.of(Err.on("title", title, "must not be blank")));
        };

        Checked checked = validate.apply("");
        // failed: INVALID_VALUE
        switch (checked.getStatus()) {
            case Passed passed -> System.out.println("ok: " + passed.getName());
            case Failed failed -> System.out.println("failed: " + failed.getName());
        }
        // </example>
        check(!checked.isValid() && checked.getStatus() instanceof Failed.Invalid, "tutorial-validate: failed");
        check(checked.getErrors().size() == 1, "tutorial-validate: one error");

        // <example id="tutorial-problem" tags="tutorial,rfc9457">
        Err.ErrorList errors = new Err.ErrorList(checked.getErrors(), "Validation failed");

        // The failure as an RFC 9457 problem, for an HTTP API
        Problem<ErrorDetail> problem = new ProblemConverter().convert(checked.getStatus(), errors);
        // 400
        System.out.println(problem.getStatus());
        // 1
        System.out.println(problem.getErrors().size());

        // The same failure as kiit's CodeDetail, self-contained, for your own services
        CodeDetail<ErrorDetail> detail = CodeDetails.toCodeDetail(checked.getStatus(), errors);
        // kiit.dev:codes:Failed:Invalid:INVALID_VALUE
        System.out.println(detail.getCode());
        // false
        System.out.println(detail.getSuccess());
        // </example>
        check(problem.getType().equals("https://www.kiit.dev/docs/kiit-codes?code=Failed:Invalid:INVALID_VALUE#taxonomy"), "tutorial-problem: type");
        check(problem.getTitle().equals("The request had an invalid value.") && problem.getStatus() == 400, "tutorial-problem: title and status");
        check("Validation failed".equals(problem.getDetail()) && problem.getErrors().size() == 1, "tutorial-problem: detail and errors");
        check("kiit.dev:codes:Failed:Invalid:INVALID_VALUE".equals(problem.getCode()), "tutorial-problem: code");
        check(detail.getCode().equals("kiit.dev:codes:Failed:Invalid:INVALID_VALUE") && !detail.getSuccess(), "tutorial-problem: detail code and success");
        check(detail.getTitle().equals("The request had an invalid value.") && "Validation failed".equals(detail.getDetail()), "tutorial-problem: detail title and text");
        check(detail.getErrors().size() == 1 && "title".equals(detail.getErrors().get(0).getField()), "tutorial-problem: detail errors");
    }

    // ============================================================
    // Guide: recipes. Each one stands alone, so the page can show it as it is.
    // ============================================================

    private static void guideBuiltins() {
        section("Guide: Status: Built-ins");

        // <example id="guide-builtins" tags="guide">
        // A specific built-in code
        Status created = Passed.Succeeded.CREATED;
        // The group's default, when you only know the kind of outcome
        Failed.Invalid failed = Failed.Invalid.DEFAULT;
        // INVALID_VALUE
        System.out.println(failed.getName());
        // true
        System.out.println(failed.isDefault());
        // false
        System.out.println(Failed.Invalid.BAD_REQUEST.isDefault());
        // </example>
        check(created.getName().equals("CREATED"), "guide-builtins: created");
        check(failed == Failed.Invalid.INVALID_VALUE && failed.isDefault(), "guide-builtins: default");
        check(!Failed.Invalid.BAD_REQUEST.isDefault(), "guide-builtins: not default");
    }

    private static void guideCustomCode() {
        section("Guide: Status: Custom code");

        // <example id="guide-custom-code" tags="guide">
        Failed.Rejected PAYMENT_DECLINED =
                new Failed.Rejected("PAYMENT_DECLINED", "Payment declined", "payments.example.com", "payments.cards");
        // </example>
        check(PAYMENT_DECLINED.getGroup().equals("Rejected"), "guide-custom-code: group");
        check(PAYMENT_DECLINED.getOrigin().equals("payments.example.com") && PAYMENT_DECLINED.getScope().equals("payments.cards"), "guide-custom-code: origin and scope");
    }

    private static void guidePatternMatching() {
        section("Guide: Status: Pattern matching");

        // <example id="guide-pattern-matching" tags="guide">
        // 1. Passed or Failed
        Function<Status, String> binary = status -> switch (status) {
            case Passed passed -> "ok: " + passed.getName();
            case Failed failed -> "failed: " + failed.getName();
        };

        // 2. Passed or Failed, each by its four groups. The compiler flags a missing group.
        Function<Status, String> nested = status -> switch (status) {
            case Passed.Succeeded s -> "done";
            case Passed.Pending p -> "in progress";
            case Passed.Excluded e -> "skipped";
            case Passed.Information i -> "for your information";
            case Failed.Restricted r -> "not allowed";
            case Failed.Invalid i -> "fix the input";
            case Failed.Rejected r -> "refused by a rule";
            case Failed.Unserved u -> "try again later";
        };

        // 3. Specific to broad: a code, then a group, then Failed or Passed
        Function<Status, String> hybrid = status -> switch (status) {
            case Failed.Rejected r when r.getName().equals("CONFLICT") -> "already exists";
            case Failed.Invalid i -> "fix the input";
            case Failed failed -> "failed: " + failed.getName();
            case Passed passed -> "ok: " + passed.getName();
        };

        // failed: INVALID_VALUE
        System.out.println(binary.apply(Failed.Invalid.INVALID_VALUE));
        // in progress
        System.out.println(nested.apply(Passed.Pending.QUEUED));
        // already exists
        System.out.println(hybrid.apply(Failed.Rejected.CONFLICT));
        // </example>
        check(binary.apply(Failed.Invalid.INVALID_VALUE).equals("failed: INVALID_VALUE") && binary.apply(Passed.Succeeded.SUCCESS).equals("ok: SUCCESS"), "guide-pattern-matching: binary");
        check(nested.apply(Passed.Succeeded.SUCCESS).equals("done") && nested.apply(Passed.Pending.QUEUED).equals("in progress"), "guide-pattern-matching: nested passed");
        check(nested.apply(Passed.Excluded.SKIPPED).equals("skipped") && nested.apply(Passed.Information.NOTICE).equals("for your information"), "guide-pattern-matching: nested passed rest");
        check(nested.apply(Failed.Invalid.INVALID_VALUE).equals("fix the input") && nested.apply(Failed.Unserved.TIMEOUT).equals("try again later"), "guide-pattern-matching: nested failed");
        check(nested.apply(Failed.Restricted.DENIED).equals("not allowed") && nested.apply(Failed.Rejected.CONFLICT).equals("refused by a rule"), "guide-pattern-matching: nested failed rest");
        check(hybrid.apply(Failed.Rejected.CONFLICT).equals("already exists"), "guide-pattern-matching: hybrid code first");
        check(hybrid.apply(Failed.Invalid.INVALID_VALUE).equals("fix the input"), "guide-pattern-matching: hybrid group");
        check(hybrid.apply(Failed.Unserved.TIMEOUT).equals("failed: TIMEOUT") && hybrid.apply(Passed.Succeeded.SUCCESS).equals("ok: SUCCESS"), "guide-pattern-matching: hybrid broad");
    }

    private static void guideCollectErrors() {
        section("Guide: Error Handling: Collect errors");

        // <example id="guide-collect-errors" tags="guide">
        Function<String, Checked> validateTitle = title -> {
            if (!title.isBlank()) {
                return Checked.success();
            }
            return Checked.failure(Failed.Invalid.INVALID_VALUE, List.of(Err.on("title", title, "must not be blank")));
        };

        Function<String, Checked> validateListId = listId -> {
            if (List.of("personal", "team").contains(listId)) {
                return Checked.success();
            }
            return Checked.failure(Failed.Invalid.NOT_FOUND, List.of(Err.on("listId", listId, "unknown list")));
        };

        Checked checked = Checks.collect(validateTitle.apply(""), validateListId.apply("unknown-list"));
        // valid = false, errors = 2
        System.out.println("valid = " + checked.isValid() + ", errors = " + checked.getErrors().size());
        // </example>
        check(!checked.isValid(), "guide-collect-errors: invalid");
        check(checked.getErrors().size() == 2, "guide-collect-errors: two errors");
    }

    private static void guideErrorDetails() {
        section("Guide: Error Handling: Error details");

        // <example id="guide-error-details" tags="guide">
        // A plain message
        Err plain = Err.of("title is required");
        // An error on one field, with its value
        Err title = Err.on("title", "", "must be 1-100 characters");
        // The same, without the value
        Err password = Err.on("password", "must be at least 12 characters");
        // Several plain messages under one message
        Err.ErrorList many = Err.list(List.of("title is required", "list is unknown"), "Validation failed");
        // </example>
        check(plain.getMessage().equals("title is required"), "guide-error-details: plain");
        check(title instanceof Err.ErrorField f && f.getField().equals("title"), "guide-error-details: field");
        check(password instanceof Err.ErrorField g && g.getField().equals("password"), "guide-error-details: no value");
        check(many.getErrors().size() == 2 && many.getMessage().equals("Validation failed"), "guide-error-details: list");
    }

    private static void guideExceptions() {
        section("Guide: Error Handling: Exceptions");

        // <example id="guide-exceptions" tags="guide">
        Function<String, Status> create = title ->
                title.equals("groceries") ? Failed.Rejected.CONFLICT : Passed.Succeeded.CREATED;

        // StatusException is a checked exception in Java
        try {
            Status status = create.apply("groceries");
            if (status instanceof Failed failed) {
                throw StatusExceptions.toException(failed);
            }
        } catch (StatusException e) {
            // CONFLICT
            System.out.println(e.getStatus().getName());
        }
        // </example>
        String caught = "";
        try {
            Status status = create.apply("groceries");
            if (status instanceof Failed failed) {
                throw StatusExceptions.toException(failed);
            }
        } catch (StatusException e) {
            caught = e.getStatus().getName() + (e instanceof StatusException.RejectedException ? " rejected" : "");
        }
        check(caught.equals("CONFLICT rejected"), "guide-exceptions: caught");
    }

    private static void guideTypeUrl() {
        section("Guide: Response: Type URL");

        // <example id="guide-type-url" tags="guide">
        Failed.Rejected stripe = new Failed.Rejected("DUPLICATE_CHARGE", "Duplicate charge", "stripe.com", "payments.cards");
        Failed.Rejected plain = new Failed.Rejected("OUT_OF_STOCK", "Out of stock", "myapp1", "");

        // 1. A domain origin and nothing registered
        // https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge
        System.out.println(new ProblemConverter().convert(stripe).getType());

        // 2. A plain id is not a domain, so the type is relative
        // /docs/codes/failed/rejected/out-of-stock
        System.out.println(new ProblemConverter().convert(plain).getType());

        // 3. A base URL registered for the origin
        // https://stripe.com/errors/payments/cards/failed/rejected/duplicate-charge
        ProblemConverter registered = new ProblemConverter(Map.of("stripe.com", "https://stripe.com/errors"));
        System.out.println(registered.convert(stripe).getType());
        // </example>
        check(new ProblemConverter().convert(stripe).getType().equals("https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge"), "guide-type-url: domain");
        check(new ProblemConverter().convert(plain).getType().equals("/docs/codes/failed/rejected/out-of-stock"), "guide-type-url: plain id");
        check(registered.convert(stripe).getType().equals("https://stripe.com/errors/payments/cards/failed/rejected/duplicate-charge"), "guide-type-url: registered");
    }

    private static void guideCustomTypeUrl() {
        section("Guide: Response: Custom type URL");

        // <example id="guide-custom-type-url" tags="guide">
        Failed.Rejected stripe = new Failed.Rejected("DUPLICATE_CHARGE", "Duplicate charge", "stripe.com", "payments.cards");
        ProblemConverter problems = new ProblemConverter();

        // 1. Your own suffix, with a type builder
        // https://stripe.com/docs/codes/charges/duplicate
        System.out.println(problems.convert(stripe, null, status -> "charges/duplicate").getType());

        // 2. Exactly one URL, a base with an empty suffix
        // https://example.com/probs/duplicate-charge
        System.out.println(problems.convertWithUrl(stripe, null, "https://example.com/probs/duplicate-charge", status -> "").getType());
        // </example>
        check(problems.convert(stripe, null, status -> "charges/duplicate").getType().equals("https://stripe.com/docs/codes/charges/duplicate"), "guide-custom-type-url: builder");
        check(problems.convertWithUrl(stripe, null, "https://example.com/probs/duplicate-charge", status -> "").getType().equals("https://example.com/probs/duplicate-charge"), "guide-custom-type-url: exact");
        check(problems.convert(stripe).getCode().equals(problems.convert(stripe, null, status -> "x").getCode()), "guide-custom-type-url: code unchanged");
    }

    private static void guideHttp() {
        section("Guide: Response: HTTP and gRPC");

        // <example id="guide-http" tags="guide">
        CodesToHttp http = new CodesToHttp();
        CodesToGrpc grpc = new CodesToGrpc();

        // 201
        System.out.println(http.toCode(Passed.Succeeded.CREATED));
        // 409
        System.out.println(http.toCode(Failed.Rejected.CONFLICT));

        // 0 (OK)
        System.out.println(grpc.toCode(Passed.Succeeded.CREATED));
        // 6 (ALREADY_EXISTS)
        System.out.println(grpc.toCode(Failed.Rejected.CONFLICT));
        // </example>
        check(http.toCode(Passed.Succeeded.CREATED) == 201 && http.toCode(Failed.Rejected.CONFLICT) == 409, "guide-http: http");
        check(grpc.toCode(Passed.Succeeded.CREATED) == 0 && grpc.toCode(Failed.Rejected.CONFLICT) == 6, "guide-http: grpc");
    }

    private static void guideCustomProtocol() {
        section("Guide: Response: Custom protocol");

        // <example id="guide-custom-protocol" tags="guide">
        Status PAYMENT_DECLINED = new Failed.Rejected("PAYMENT_DECLINED", "Payment declined", "payments.example.com", "");

        CompositeLookup http = new CompositeLookup(new CodesToHttp(), Map.of(PAYMENT_DECLINED, 402));
        // 402
        System.out.println(http.toCode(PAYMENT_DECLINED));
        // 409
        System.out.println(http.toCode(Failed.Rejected.CONFLICT));
        // </example>
        check(http.toCode(PAYMENT_DECLINED) == 402, "guide-custom-protocol: extension");
        check(http.toCode(Failed.Rejected.CONFLICT) == 409, "guide-custom-protocol: falls back to the base");
    }
}
