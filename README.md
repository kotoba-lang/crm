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

First consumer: [`cloud-itonami-isic-5820`](https://github.com/cloud-itonami/cloud-itonami-isic-5820)
(commercial CRM/subscription-commerce SaaS platform business, the
Salesforce/HubSpot-class vertical). Designed to be reused by future
sibling actors covering marketing-automation and customer-service hub
business models without re-deriving stage-graph or revenue-recognition
logic per actor.

## Scope (deliberately narrow)

- `pipeline`: linear stage graphs with a single ordered path plus flat
  exit stages. Branching/parallel or re-enterable stage graphs are out of
  scope.
- `revrec`: single-performance-obligation, fixed-fee, straight-line term
  subscriptions only. Usage-based billing, contract modifications, and
  multi-element arrangement allocation (ASC 606 step 4) are NOT modeled.

## Test

```bash
clojure -M:test
```
