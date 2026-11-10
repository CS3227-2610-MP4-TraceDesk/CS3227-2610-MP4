package com.tracedesk.auth.investigation.ai;

import java.util.List;
import java.util.UUID;

/** Parsed selection retained only in trusted request-local state. */
public record InvestigationAiSelection(List<String> reportFields, List<SelectedNote> notes) {
    public record SelectedNote(UUID id, int expectedVersion) { }
}
