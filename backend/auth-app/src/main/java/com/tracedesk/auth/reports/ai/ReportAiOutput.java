package com.tracedesk.auth.reports.ai;

import com.tracedesk.auth.reports.application.ReportService;
import com.tracedesk.auth.reports.domain.ReportData;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Parses the entire advisory response; no partial salvage or model-owned provenance. */
public final class ReportAiOutput {
    private ReportAiOutput() { }
    static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public record Change(String field,String replacement) {
        @Override public String toString(){return "Change[redacted]";}
    }
    public record Suggestion(String capability,String promptVersion,ReportAiService.SourceKind sourceKind,UUID sourceId,
                             int sourceVersion,List<Change> changes,List<String> missingInformation) {
        @Override public String toString(){return "Suggestion[redacted]";}
    }
    public static Suggestion parse(String text,ReportAiService.SourceKind kind,UUID source,int version) {
        try {
            if(text==null||text.getBytes(StandardCharsets.UTF_8).length>16384) throw invalid();
            var root=JSON.readTree(text); exact(root,Set.of("changes","missingInformation"));
            var changes=root.get("changes");var questions=root.get("missingInformation");
            if(!changes.isArray()||changes.size()>6||!questions.isArray()||questions.size()>5) throw invalid();
            var output=new ArrayList<Change>();var seen=new HashSet<String>();
            for(var change:changes) {
                exact(change,Set.of("field","replacement"));
                if(!change.get("field").isString()||!change.get("replacement").isString()) throw invalid();
                String field=change.get("field").asString(),replacement=change.get("replacement").asString();
                if(!ReportData.FIELD_NAMES.subList(0,6).contains(field)||!seen.add(field)||blank(replacement)) throw invalid();
                String[] values={"","","","","","",""};values[ReportData.FIELD_NAMES.indexOf(field)]=replacement;
                if(ReportData.validateDraft(new ReportData(values[0],values[1],values[2],values[3],values[4],values[5],values[6]))!=null) throw invalid();
                output.add(new Change(field,replacement));
            }
            var missing=new ArrayList<String>();
            for(var question:questions) {
                if(!question.isString()) throw invalid();String q=question.asString();
                if(blank(q)||q.codePointCount(0,q.length())>500||q.getBytes(StandardCharsets.UTF_8).length>2000
                        ||ReportData.validateDraft(new ReportData("",q,"","","","",""))!=null) throw invalid();
                missing.add(q);
            }
            return new Suggestion("REPORT_IMPROVEMENT",ReportAiService.PROMPT_VERSION,kind,source,version,List.copyOf(output),List.copyOf(missing));
        } catch(RuntimeException failure) { throw invalid(); }
    }
    static void exact(JsonNode node,Set<String> keys) {
        if(node==null||!node.isObject()||node.size()!=keys.size()||!keys.containsAll(node.propertyNames())) throw invalid();
    }
    private static boolean blank(String value) { return value.codePoints().noneMatch(cp->!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)); }
    private static ReportService.Failure invalid(){return new ReportService.Failure("AI_INVALID_OUTPUT",502);}
}
