# kotoba-lang/crm

Portable `.cljc` technical commons for CRM/subscription-commerce style
`cloud-itonami-*` actors — the reusable pieces that don't belong to any
one vertical:

- `kotoba.crm.pipeline` — generic ordered-stage transition validator for
  any funnel/pipeline domain (sales opportunity stages, marketing
  lifecycle stages, support-ticket lifecycle). No skipping ahead; exit
  stages (e.g. `:closed-lost`) reachable from any non-terminal stage;
  nothing transitions further once terminal.
- `kotoba.crm.revrec` — straight-line subscription revenue-recognition
  recompute (FASB ASC 606 / IASB IFRS 15), a pure ground-truth recompute
  in the same family `cloud-itonami-isic-6492`/`cloud-itonami-isic-6920`
  established elsewhere in this fleet.
- `kotoba.crm.leadscore` — deterministic weighted-point marketing
  lead-scoring recompute, the same pure ground-truth-recompute family as
  `revrec` applied to lead score instead of recognized revenue. Unlike
  `revrec` there is no external standard-setter to cite for the point
  weights, so the point table is this namespace's own documented spec.

First consumers: [`cloud-itonami-isic-5820`](https://github.com/cloud-itonami/cloud-itonami-isic-5820)
(commercial CRM/subscription-commerce SaaS platform business, the
Salesforce/HubSpot-class vertical) uses `pipeline` and `revrec`;
`cloud-itonami-isic-6201` (marketing-automation SaaS platform business,
the HubSpot Marketing Hub/Salesforce Marketing Cloud-class vertical)
uses `pipeline` and `leadscore`. Designed to be reused by further
sibling actors without re-deriving stage-graph, revenue-recognition, or
lead-scoring logic per actor.

## Scope (deliberately narrow)

- `pipeline`: linear stage graphs with a single ordered path plus flat
  exit stages. Branching/parallel or re-enterable stage graphs are out of
  scope.
- `revrec`: single-performance-obligation, fixed-fee, straight-line term
  subscriptions only. Usage-based billing, contract modifications, and
  multi-element arrangement allocation (ASC 606 step 4) are NOT modeled.
- `leadscore`: a fixed weighted-point sum over a documented event-kind
  table only. No ML/predictive scoring, no per-account custom weight
  overrides, and no decay of a lead's score for inactivity are modeled;
  per-event recency time-decay is supported but opt-in and off by
  default.

## Test

```bash
clojure -M:test
```
