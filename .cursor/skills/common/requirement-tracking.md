# Requirement tracking rules

When to create, update, or split requirements; how to keep ids and traceability consistent. Apply these rules during spec updates (e.g. nova-code update-specs sub-skill).

## When to create a new requirement

- The change introduces a **new capability or contract** (new behavior, new API, new acceptance criterion that is independently testable).
- A **new asset** is added that fulfills a **distinct role** not already covered by an existing requirement.
- The change is **out of scope** of any existing requirement's statement and acceptance criteria — do not force-fit; add a new requirement and link assets/tests.

## When to update an existing requirement

- The change **refines or extends** behavior that already falls under the requirement's statement and acceptance (e.g. edge case, new rule in behavior, new example). Update the requirement in place (statement, acceptance, behavior, validation) and keep one requirement id.

## When to split a requirement

- The requirement has become **too broad**: it describes multiple independently testable behaviors or multiple distinct outcomes; splitting allows separate status, tests, and traceability per part.
- **Different assets** implement different parts of the same high-level feature; each part that is independently verifiable should be a separate requirement, with assets/symbols pointing to the right requirement.
- **Status or priority** would differ by sub-behavior (e.g. one part accepted, another draft); split so each requirement has a single status.
- Give each new requirement its own **validation.tests** (partition or copy from the old requirement); ensure each test verifies exactly one of the new requirements (or attach the same test to each requirement it verifies — see Ids and traceability). If a single test verifies more than one new requirement, attach that test to each requirement it verifies (duplicate ref) or to the primary one and document in acceptance/intent; ensure every new requirement has at least one test where possible.

## When not to split

- A single test or asset already verifies the whole requirement; no need to split for the sake of granularity.
- The sub-parts are not independently testable or traceable; keep one requirement and document the behavior in acceptance/behavior.

## Procedure for splitting a requirement

1. Create new requirement entries with new ids; for each, set statement, acceptance, behavior, priority, type, and **validation.tests** (assign tests from the old requirement to the new one(s) they verify).
2. Set each new requirement's **traceability** (assets and symbols) to the set that implement that part.
3. Update **assets[].requires** and **assets[].symbols[].requires**: replace the old requirement id with the new id(s) that the asset/symbol actually implements.
4. Update **dependencies.items[].used_by_requirements**: replace the old id with the new id(s) that use that dependency.
5. Set the **old requirement** to `status: deprecated` (or remove only if fully replaced and permitted); do not leave the old id referenced in assets/dependencies.

## Ids and traceability

- **New requirements:** Use the registry's id convention (e.g. REQ-XXX-NNN). Update **assets[].requires**, **assets[].symbols[].requires**, traceability, validation.tests (per requirement), and **dependencies.items[].used_by_requirements** so no dangling refs.
- **When splitting:** Create new requirement entries with new ids; update assets.requires, assets.symbols[].requires, and tests to point to the correct requirement(s). Deprecate or remove the old id only if it is fully replaced; prefer "deprecate" and raise an issue if the old behavior is removed without explicit user permission.
