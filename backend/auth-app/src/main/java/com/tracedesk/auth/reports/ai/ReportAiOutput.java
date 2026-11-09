package com.tracedesk.auth.reports.ai;

import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Parses the entire advisory response; no partial salvage or model-owned provenance. */
public final class ReportAiOutput {
    private ReportAiOutput() { }
    static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    enum Stage { GATEWAY_OUTPUT_REJECTED, ROLE_RESPONSE_SHAPE, ROLE_CHANGE_VALIDATION, ROLE_QUESTION_VALIDATION }
    enum Reason { OUTPUT_LIMIT_EXCEEDED, GATEWAY_FORMAT_REJECTED, MALFORMED_JSON, RESPONSE_INVALID,
        TOP_LEVEL_KEYS_INVALID, CHANGES_CONTAINER_INVALID, QUESTIONS_CONTAINER_INVALID, CHANGE_ENTRY_INVALID,
        CHANGE_FIELD_INVALID, CHANGE_FIELD_DUPLICATE, REPLACEMENT_INVALID, QUESTION_INVALID, QUESTION_LIMIT_EXCEEDED }
    record Diagnostic(Stage stage, Reason reason) { }
    public record Change(String field,String replacement) {
        @Override public String toString(){return "Change[redacted]";}
    }
    public record Suggestion(String capability,String promptVersion,ReportAiService.SourceKind sourceKind,UUID sourceId,
                             int sourceVersion,List<Change> changes,List<String> missingInformation) {
        @Override public String toString(){return "Suggestion[redacted]";}
    }
    private static final class Invalid extends RuntimeException {
        final Diagnostic diagnostic;
        Invalid(Stage stage, Reason reason) { super(null, null, false, false); diagnostic=new Diagnostic(stage,reason); }
    }
    public static Suggestion parse(String text,ReportAiService.SourceKind kind,UUID source,int version) {
        return parse(text,kind,source,version,ignored -> { });
    }
    static Suggestion parse(String text,ReportAiService.SourceKind kind,UUID source,int version,Consumer<Diagnostic> sink) {
        try {
            if(text==null) throw invalid(Stage.ROLE_RESPONSE_SHAPE,Reason.RESPONSE_INVALID);
            if(text.getBytes(StandardCharsets.UTF_8).length>16384) throw invalid(Stage.ROLE_RESPONSE_SHAPE,Reason.OUTPUT_LIMIT_EXCEEDED);
            final JsonNode root;
            try { root=JSON.readTree(text); }
            catch(RuntimeException malformed) { throw invalid(Stage.ROLE_RESPONSE_SHAPE,Reason.MALFORMED_JSON); }
            if(root==null||!root.isObject()) throw invalid(Stage.ROLE_RESPONSE_SHAPE,Reason.RESPONSE_INVALID);
            exact(root,Set.of("changes","missingInformation"),Stage.ROLE_RESPONSE_SHAPE,Reason.TOP_LEVEL_KEYS_INVALID);
            var changes=root.get("changes");var questions=root.get("missingInformation");
            if(!changes.isArray()) throw invalid(Stage.ROLE_RESPONSE_SHAPE,Reason.CHANGES_CONTAINER_INVALID);
            if(changes.size()>6) throw invalid(Stage.ROLE_RESPONSE_SHAPE,Reason.CHANGES_CONTAINER_INVALID);
            if(!questions.isArray()) throw invalid(Stage.ROLE_RESPONSE_SHAPE,Reason.QUESTIONS_CONTAINER_INVALID);
            if(questions.size()>5) throw invalid(Stage.ROLE_QUESTION_VALIDATION,Reason.QUESTION_LIMIT_EXCEEDED);
            var output=new ArrayList<Change>();var seen=new HashSet<String>();
            for(var change:changes) {
                if(change==null||!change.isObject()) throw invalid(Stage.ROLE_CHANGE_VALIDATION,Reason.CHANGE_ENTRY_INVALID);
                exact(change,Set.of("field","replacement"),Stage.ROLE_CHANGE_VALIDATION,Reason.CHANGE_ENTRY_INVALID);
                if(!change.get("field").isString()) throw invalid(Stage.ROLE_CHANGE_VALIDATION,Reason.CHANGE_FIELD_INVALID);
                if(!change.get("replacement").isString()) throw invalid(Stage.ROLE_CHANGE_VALIDATION,Reason.REPLACEMENT_INVALID);
                String field=change.get("field").asString(),replacement=change.get("replacement").asString();
                if(!ReportData.FIELD_NAMES.subList(0,6).contains(field)) throw invalid(Stage.ROLE_CHANGE_VALIDATION,Reason.CHANGE_FIELD_INVALID);
                if(!seen.add(field)) throw invalid(Stage.ROLE_CHANGE_VALIDATION,Reason.CHANGE_FIELD_DUPLICATE);
                if(blank(replacement)) throw invalid(Stage.ROLE_CHANGE_VALIDATION,Reason.REPLACEMENT_INVALID);
                String[] values={"","","","","","",""};values[ReportData.FIELD_NAMES.indexOf(field)]=replacement;
                if(ReportData.validateDraft(new ReportData(values[0],values[1],values[2],values[3],values[4],values[5],values[6]))!=null)
                    throw invalid(Stage.ROLE_CHANGE_VALIDATION,Reason.REPLACEMENT_INVALID);
                output.add(new Change(field,replacement));
            }
            var missing=new ArrayList<String>();
            for(var question:questions) {
                if(!question.isString()) throw invalid(Stage.ROLE_QUESTION_VALIDATION,Reason.QUESTION_INVALID);
                String q=question.asString();
                if(blank(q)||q.codePointCount(0,q.length())>500||q.getBytes(StandardCharsets.UTF_8).length>2000
                        ||ReportData.validateDraft(new ReportData("",q,"","","","",""))!=null)
                    throw invalid(Stage.ROLE_QUESTION_VALIDATION,Reason.QUESTION_INVALID);
                missing.add(q);
            }
            return new Suggestion("REPORT_IMPROVEMENT",ReportAiService.PROMPT_VERSION,kind,source,version,List.copyOf(output),List.copyOf(missing));
        } catch(Invalid failure) {
            try { if(sink!=null) sink.accept(failure.diagnostic); } catch(RuntimeException ignored) { }
            throw publicInvalid();
        } catch(RuntimeException unexpected) {
            var diagnostic=new Diagnostic(Stage.ROLE_RESPONSE_SHAPE,Reason.RESPONSE_INVALID);
            try { if(sink!=null) sink.accept(diagnostic); } catch(RuntimeException ignored) { }
            throw publicInvalid();
        }
    }
    static void exact(JsonNode node,Set<String> keys,Stage stage,Reason reason) {
        if(node==null||!node.isObject()||node.size()!=keys.size()||!keys.containsAll(node.propertyNames())) throw invalid(stage,reason);
    }
    private static boolean blank(String value) { return value.codePoints().noneMatch(cp->!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)); }
    static ReportService.Failure publicInvalid(){return new ReportService.Failure("AI_INVALID_OUTPUT",502);}
    private static Invalid invalid(Stage stage,Reason reason){return new Invalid(stage,reason);}
}
