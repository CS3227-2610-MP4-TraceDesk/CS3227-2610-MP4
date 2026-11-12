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

    private InvestigationAiOutput() { }
    public record Hypothesis(String statement, String rationale, List<String> sources) { }
    public record Result(List<Hypothesis> hypotheses, List<String> nextSteps) { }

    public static Result parse(String completion, Set<String> sentLabels) {
        if (completion == null || sentLabels == null
                || completion.getBytes(StandardCharsets.UTF_8).length > 16384) throw invalid();
        final JsonNode root;
        try {
            root = JSON.readTree(completion);
        } catch (RuntimeException malformed) {
            // Do not attach parser exceptions: their messages can contain provider text.
            throw invalid();
        }
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
                        || !sentLabels.contains(label) || !seen.add(label)) throw invalid();
                labels.add(label);
            }
            parsedHypotheses.add(new Hypothesis(statement, rationale, List.copyOf(labels)));
        }
        var parsedSteps = new ArrayList<String>();
        for (JsonNode step : nextSteps) parsedSteps.add(text(step, 240, 960));
        return new Result(List.copyOf(parsedHypotheses), List.copyOf(parsedSteps));
    }

    private static void exact(JsonNode node, Set<String> keys) {
        if (node == null || !node.isObject() || node.size() != keys.size()
                || !keys.containsAll(node.propertyNames())) throw invalid();
    }

    private static void array(JsonNode node, int minimum, int maximum) {
        if (node == null || !node.isArray() || node.size() < minimum || node.size() > maximum) throw invalid();
    }

    private static String text(JsonNode node, int maxCodePoints, int maxBytes) {
        if (node == null || !node.isString()) throw invalid();
        String value = node.asString();
        if (value.codePointCount(0, value.length()) > maxCodePoints
                || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw invalid();
        boolean nonblank = false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            // codePointAt leaves an unpaired UTF-16 surrogate in the surrogate range.
            if ((cp >= 0xd800 && cp <= 0xdfff)
                    || (Character.isISOControl(cp) && cp != '\t' && cp != '\n' && cp != '\r')) throw invalid();
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) nonblank = true;
            offset += Character.charCount(cp);
        }
        if (!nonblank) throw invalid();
        return value;
    }

    private static ReportService.Failure invalid() {
        return new ReportService.Failure("AI_INVALID_OUTPUT", 502);
    }
}
