# QA AI Review and Follow-up

## Making suggestions usable

**Prompt strategy.** I asked for QA AI review to work with the configured provider, support follow-up questions, and let a reviewer explicitly add useful suggestions to verification.

**AI contribution and corrections.** The QA AI contract describes a versioned planning and follow-up flow, a provider disclosure, and explicit addition of selected checks as Pending. The reviewer still decides what to test and saves the verification draft; the model does not close the bug or apply a code fix.

**Verification and judgement.** The [QA AI specification](../../workflow/specs/qa-ai.md#verification) records local browser and backend checks, including a signed-in live follow-up observation. The [role AI validation record](../../workflow/qa/evidence/role-ai-validation.md) notes that the observation did not capture a pinned candidate, measured context, costs or a quality evaluation. It is not proof of deployed readiness or model accuracy.

**Reflection takeaway.** A live response demonstrates that a particular interaction worked; it does not prove suggestions are correct or that every security and deployment gate passed.
