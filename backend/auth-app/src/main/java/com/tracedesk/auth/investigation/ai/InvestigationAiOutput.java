package com.tracedesk.auth.investigation.ai;

import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates the entire advisory reply without salvaging or normalizing model text. */
public final class InvestigationAiOutput {
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final JsonMapper JSON_ALLOW_DUPLICATES = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    enum InvalidReason {
        CODE_FENCE, TRAILING_CONTENT, EXTRA_KEY, MISSING_KEY, DUPLICATE_KEY, WRONG_TYPE,
        LIMIT_EXCEEDED, UNKNOWN_SOURCE_LABEL, DUPLICATE_SOURCE_LABEL, EMPTY_TEXT, INVALID_TEXT, NOT_JSON
    }

    private static final class Invalid extends RuntimeException {
        private final InvalidReason reason;

        private Invalid(InvalidReason reason) {
            super(null, null, false, false);
            this.reason = reason;
        }
    }

    private InvestigationAiOutput() { }
    public record Hypothesis(String statement, String rationale, List<String> sources) { }
    public record Result(List<Hypothesis> hypotheses, List<String> nextSteps) { }

    public static Result parse(String completion, Set<String> sentLabels) {
        try {
            return parseValidated(completion, sentLabels);
        } catch (Invalid failure) {
            throw invalid();
        } catch (RuntimeException unexpected) {
            throw invalid();
        }
    }

    static InvalidReason invalidReason(String completion, Set<String> sentLabels) {
        try {
            parseValidated(completion, sentLabels);
            return null;
        } catch (Invalid failure) {
            return failure.reason;
        } catch (RuntimeException unexpected) {
            return InvalidReason.NOT_JSON;
        }
    }

    private static Result parseValidated(String completion, Set<String> sentLabels) {
        if (completion == null) throw invalid(InvalidReason.NOT_JSON);
        if (sentLabels == null) throw invalid(InvalidReason.WRONG_TYPE);
        if (completion.getBytes(StandardCharsets.UTF_8).length > 16384)
            throw invalid(InvalidReason.LIMIT_EXCEEDED);
        if (completion.stripLeading().startsWith("```")) throw invalid(InvalidReason.CODE_FENCE);
        JsonNode root = root(completion);
        exact(root, Set.of("hypotheses", "nextSteps"));
        JsonNode hypotheses = root.get("hypotheses"), nextSteps = root.get("nextSteps");
        array(hypotheses, 0, 3);
        array(nextSteps, 0, 4);

        var parsedHypotheses = new ArrayList<Hypothesis>();
        for (JsonNode hypothesis : hypotheses) {
            exact(hypothesis, Set.of("statement", "rationale", "sources"));
            String statement = text(hypothesis.get("statement"), 300, 1200);
            String rationale = text(hypothesis.get("rationale"), 500, 2000);
            JsonNode sources = hypothesis.get("sources");
            array(sources, 1, 4);
            var labels = new ArrayList<String>();
            var seen = new HashSet<String>();
            for (JsonNode source : sources) {
                String label = text(source, 17, 68);
                if ((!ReportData.FIELD_NAMES.contains(label) && !label.matches("note[1-5]"))
                        || !sentLabels.contains(label)) throw invalid(InvalidReason.UNKNOWN_SOURCE_LABEL);
                if (!seen.add(label)) throw invalid(InvalidReason.DUPLICATE_SOURCE_LABEL);
                labels.add(label);
            }
            parsedHypotheses.add(new Hypothesis(statement, rationale, List.copyOf(labels)));
        }
        var parsedSteps = new ArrayList<String>();
        for (JsonNode step : nextSteps) parsedSteps.add(text(step, 240, 960));
        return new Result(List.copyOf(parsedHypotheses), List.copyOf(parsedSteps));
    }

    private static JsonNode root(String completion) {
        try {
            JsonNode root = JSON.readTree(completion);
            if (root == null) throw invalid(InvalidReason.NOT_JSON);
            return root;
        } catch (Invalid failure) {
            throw failure;
        } catch (RuntimeException strictFailure) {
            if (hasTrailingContainer(completion)) throw invalid(InvalidReason.TRAILING_CONTENT);
            try {
                JSON_ALLOW_DUPLICATES.readTree(completion);
                throw invalid(InvalidReason.DUPLICATE_KEY);
            } catch (Invalid failure) {
                throw failure;
            } catch (RuntimeException duplicateOrSyntaxFailure) {
                throw invalid(InvalidReason.NOT_JSON);
            }
        }
    }

    private static boolean hasTrailingContainer(String value) {
        int start = 0;
        while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
        if (start == value.length() || (value.charAt(start) != '{' && value.charAt(start) != '[')) return false;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char character = value.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
                continue;
            }
            if (character == '"') quoted = true;
            else if (character == '{' || character == '[') depth++;
            else if (character == '}' || character == ']') {
                depth--;
                if (depth == 0) return !value.substring(index + 1).isBlank();
                if (depth < 0) return false;
            }
        }
        return false;
    }

    private static void exact(JsonNode node, Set<String> keys) {
        if (node == null || !node.isObject()) throw invalid(InvalidReason.WRONG_TYPE);
        if (!keys.containsAll(node.propertyNames())) throw invalid(InvalidReason.EXTRA_KEY);
        if (node.size() != keys.size()) throw invalid(InvalidReason.MISSING_KEY);
    }

    private static void array(JsonNode node, int minimum, int maximum) {
        if (node == null || !node.isArray()) throw invalid(InvalidReason.WRONG_TYPE);
        if (node.size() < minimum || node.size() > maximum) throw invalid(InvalidReason.LIMIT_EXCEEDED);
    }

    private static String text(JsonNode node, int maxCodePoints, int maxBytes) {
        if (node == null || !node.isString()) throw invalid(InvalidReason.WRONG_TYPE);
        String value = node.asString();
        if (value.codePointCount(0, value.length()) > maxCodePoints
                || value.getBytes(StandardCharsets.UTF_8).length > maxBytes)
            throw invalid(InvalidReason.LIMIT_EXCEEDED);
        boolean nonblank = false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            // codePointAt leaves an unpaired UTF-16 surrogate in the surrogate range.
            if ((cp >= 0xd800 && cp <= 0xdfff)
                    || (Character.isISOControl(cp) && cp != '\t' && cp != '\n' && cp != '\r'))
                throw invalid(InvalidReason.INVALID_TEXT);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) nonblank = true;
            offset += Character.charCount(cp);
        }
        if (!nonblank) throw invalid(InvalidReason.EMPTY_TEXT);
        return value;
    }

    private static Invalid invalid(InvalidReason reason) {
        return new Invalid(reason);
    }

    private static ReportService.Failure invalid() {
        return new ReportService.Failure("AI_INVALID_OUTPUT", 502);
    }
}
