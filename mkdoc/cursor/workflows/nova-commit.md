# Nova-commit

## Summary

Full-suite verification and commit. Discovery (all registries) → gates → tests → plausibility → docs reconcile → static analysis → commit/push → output. Do not commit until all phases pass. `docs_reconcile` is for docs/spec alignment only and must not re-run unit tests.

## Sequence

1. discovery  
2. schema_gate  
3. drift_gate  
4. run_tests  
5. plausibility_review  
6. docs_reconcile  
7. build_check  
8. commit_push  
9. output  


