# Operator quickstart

`kotoba-lang/crm` is a **pure `.cljc` library**. There is no service, no
deployment, no scheduled job, and no I/O anywhere in it — so "operating" it
never means running it. An operator here is the person **wiring these four
namespaces into an actor's governor or dashboard**, and the thing that can go
wrong is not an outage: it is a governor that reads one of these functions'
`nil` as a zero and passes a materially wrong number through unchallenged.

Everything below was executed against commit `a648f40` (`main`, and the
`manifest/west.yml` pin, all three in agreement) on 2026-08-17. Outputs are
pasted as they were printed, not as they are expected to look. Every example
fixes its own `:as-of-date`, so re-running these gives the same numbers on any
later day.

## 1. Verify the checkout (about 8 seconds)

```bash
clojure -M:test
```

```
Ran 21 tests containing 89 assertions.
0 failures, 0 errors.
```

```bash
clojure -M:lint
```

```
linting took 987ms, errors: 0, warnings: 0
```

(The duration is whatever your machine is doing — this repo linted in 987ms and
3478ms on two consecutive runs on the same loaded workstation. `errors: 0,
warnings: 0` is the part that is a claim.)

**If either is red before you have changed anything, stop and fix that first.**
You cannot judge your own change against a baseline you have not seen green.

## 2. Depend on it

The library has **zero runtime dependencies** (`:deps {}` in `deps.edn`; the
test-runner and clj-kondo are alias-only). Verified, not assumed: a fresh
`git clone` outside this workspace runs the same 21 tests / 89 assertions green.

In-workspace consumers use a relative `:local/root` — this is the real form,
copied from the two live consumers:

```clojure
io.github.kotoba-lang/crm {:local/root "../../kotoba-lang/crm"}
```

Outside the workspace, pin the SHA:

```clojure
io.github.kotoba-lang/crm {:git/url "https://github.com/kotoba-lang/crm.git"
                           :git/sha "a648f40c5c272e9bfba79f6b1407bb43f7402d16"}
```

Verified end to end from an empty directory:

```bash
clojure -Sdeps '{:deps {io.github.kotoba-lang/crm {:git/url "https://github.com/kotoba-lang/crm.git" :git/sha "a648f40c5c272e9bfba79f6b1407bb43f7402d16"}}}' \
  -M -e "(require (quote [kotoba.crm.leadscore :as l])) (println :git-dep-ok (l/recompute-score [{:kind :demo-request}]))"
```

```
:git-dep-ok 20
```

## 3. Five-minute tour

Start a REPL at the repo root (`clj`) and require all four:

```clojure
(require '[kotoba.crm.pipeline  :as pipeline]
         '[kotoba.crm.revrec    :as revrec]
         '[kotoba.crm.leadscore :as leadscore]
         '[kotoba.crm.funnel    :as funnel])
```

### pipeline — is this stage transition legal?

The stage vocabulary is **yours**, not the library's. This is the real sales
pipeline from `cloud-itonami-isic-5820`:

```clojure
(def sales [:prospecting :qualification :proposal :negotiation :closed-won])
(def exits #{:closed-lost})

(pipeline/valid-transition? sales exits :prospecting :qualification) ;=> true
(pipeline/valid-transition? sales exits :prospecting :negotiation)   ;=> false   ; no skipping ahead
(pipeline/valid-transition? sales exits :qualification :closed-lost) ;=> true    ; exit from anywhere
(pipeline/valid-transition? sales exits :closed-won :closed-lost)    ;=> false   ; terminal accepts nothing

(pipeline/next-stages sales exits :proposal)    ;=> #{:closed-lost :negotiation}
(pipeline/next-stages sales exits :closed-lost) ;=> #{}
```

`cloud-itonami-isic-6201` reuses the same functions with a completely different
vocabulary (`[:subscriber :lead :mql :sql :customer]`, exits
`#{:unsubscribed :bounced :disqualified}`). If you are tempted to fork this
namespace to add your stages, you do not need to — pass your own vector.

### revrec — recompute the revenue, do not trust the claim

Straight-line ASC 606 / IFRS 15. A $12,000 annual subscription starting
2026-01-01, seen on 2026-08-17:

```clojure
(def sub {:contract-value-usd 12000 :term-months 12
          :start-date {:year 2026 :month 1 :day 1}
          :as-of-date {:year 2026 :month 8 :day 17}})

(revrec/elapsed-months sub)               ;=> 7
(revrec/recognized-revenue-to-date sub)   ;=> 7000.0

(revrec/mismatch sub 7000.00)   ;=> nil        ; within tolerance — nothing to say
(revrec/mismatch sub 12000.00)
;=> {:recomputed-usd 7000.0, :proposed-usd 12000.0, :delta-usd 5000.0,
;    :spec-basis #{:fasb-asc-606 :iasb-ifrs-15}}
```

That second call is the whole point of the namespace: someone proposed booking
the full contract value at month 7, and the recompute caught $5,000 of it.

### leadscore — same shape, different quantity

```clojure
(def hist [{:kind :email-open} {:kind :email-click} {:kind :pricing-page-view}
           {:kind :form-fill}  {:kind :demo-request}])

(leadscore/recompute-score hist)   ;=> 39      ; 1 + 3 + 5 + 10 + 20
(leadscore/mismatch hist 45)       ;=> {:recomputed 39, :proposed 45, :delta 6}
(leadscore/recompute-score nil)    ;=> 0       ; no engagement really is zero
```

Unknown event kinds score 0 points **silently**, so surface them explicitly:

```clojure
(leadscore/unrecognized-events (conj hist {:kind :webinar-attend}))
;=> ({:kind :webinar-attend})
```

A governor that never calls `unrecognized-events` will read a lead with fifty
`:webinar-attend` events as a score of 0 and never say why.

### funnel — aggregate across records

```clojure
(def ents [{:id 1 :stage :prospecting}
           {:id 2 :stage :qualification}
           {:id 3 :stage :proposal}
           {:id 4 :stage :closed-won}
           {:id 5 :stage :closed-lost}
           {:id 6 :stage :closed-lost :reached-stage :proposal}])

(funnel/stage-counts ents sales)
;=> {:prospecting 1, :qualification 1, :proposal 1, :negotiation 0,
;    :closed-won 1, :closed-lost 2}

(funnel/reached-counts ents sales)
;=> {:prospecting 5, :qualification 4, :proposal 3, :negotiation 1, :closed-won 1}

(funnel/conversion-rate ents sales)
;=> {[:prospecting :qualification] 0.8,
;    [:qualification :proposal] 0.75,
;    [:proposal :negotiation] 0.3333333333333333,
;    [:negotiation :closed-won] 1.0}
```

**Read entities 5 and 6 before you build a dashboard on this.** They are both
`:closed-lost` and they are counted differently on purpose. Entity 6 carries an
explicit `:reached-stage :proposal` fact, so it is credited with having reached
prospecting, qualification and proposal. Entity 5 carries nothing, so which
forward stage it exited from is **not recoverable**, and the library refuses to
guess — it is excluded from `reached-counts` entirely, including stage 0. That
is why six entities produce a top-of-funnel count of five.

If your funnel appears to "lose" records, this is almost certainly why, and the
fix is in **your** store, not here: record the last ordered stage at the moment
you write the exit transition.

## 4. The four refusals — `nil` means "no opinion", never zero

This is the part a governor gets wrong. Each of these returns `nil` rather than
guessing, and **substituting a default for any of them re-introduces exactly
the bug the recompute exists to catch**:

```clojure
(revrec/recognized-revenue-to-date (assoc sub :term-months 0))
;=> nil   ; a non-positive term is not a 0-dollar subscription

(leadscore/recompute-score hist {:decay-half-life-days 30})
;=> nil   ; decay requested without :as-of-date — no reference date is invented

(funnel/conversion-rate [] sales)
;=> {[:prospecting :qualification] nil, [:qualification :proposal] nil,
;    [:proposal :negotiation] nil, [:negotiation :closed-won] nil}
;    ; "no data", not "0% conversion", and not a division-by-zero crash
```

The fourth is `reached-counts` silently dropping an exited entity with no
`:reached-stage`, described above.

None of these throw. That is deliberate: a governor can turn every one of them
into a SOFT always-escalate gate instead of a crash.

## 5. Wire it into a governor

The live integration to copy is `cloud-itonami-isic-5820`'s
`src/crm/policy.cljc` (see `revenue-mismatch-imminent?`, line 211): when
a proposal closes an opportunity as `:closed-won` with a `booked-amount-usd`,
it looks up the subscription, calls `revrec/mismatch` with the actor's own
tolerance, and turns a non-`nil` result into an escalation flag on the
`check` result — it does **not** reject the proposal on its own.

`cloud-itonami-isic-6201`'s `src/marketing/policy.cljc` does the same with
`leadscore/mismatch` before allowing a score-driven stage transition, and both
actors use `funnel` from their `dashboard.cljc` only.

The division of labour that matters: **this library never decides.** It
recomputes and reports a difference; the governor owns the policy about what a
difference means.

## 6. Check the scope before you extend anything

Three of the four namespaces report their own honest coverage, machine-readably:

```clojure
(:model (revrec/coverage))     ;=> :straight-line-only
(:model (leadscore/coverage))  ;=> :fixed-weighted-point
(:model (funnel/coverage))     ;=> :point-in-time-snapshot-funnel

revrec/spec-basis              ;=> #{:fasb-asc-606 :iasb-ifrs-15}
```

Each `coverage` map's `:note` names what is **not** modeled — usage-based
billing and multi-element ASC 606 step-4 allocation in `revrec`, ML scoring and
inactivity decay in `leadscore`, time-in-stage and cohort trending in `funnel`.
Those are not oversights and not a backlog; they are boundaries. If your case
falls outside one, add a genuinely new documented method with a citable basis
where one exists. Do not widen an existing function to cover it by guessing —
`leadscore/point-values` is explicitly this namespace's own spec and not an
authoritative standard, and `revrec` is the opposite (it cites two real
standard-setters and must keep citing them).

## 7. When something is wrong

| Symptom | Almost always |
|---|---|
| Funnel top-of-funnel is lower than your record count | exited entities without `:reached-stage` — §3, entities 5 vs 6 |
| A conversion rate is `nil` | nobody reached the `from` stage; `nil` is "no data", do not render it as 0% |
| `recompute-score` returns `nil` | decay half-life passed without `:as-of-date`, or vice versa |
| A recompute returns `nil` and your number "disappears" | a precondition failed; find it, do not default it |
| A lead scores lower than expected | unrecognized event kinds — call `unrecognized-events` |
| A legal-looking transition is rejected | the `from` stage is already terminal (last ordered stage or an exit), which accepts nothing |

Before changing any source file, run `clojure -M:test` and note the counts. The
suite is one test namespace per source namespace; a change that breaks an
invariant should turn a specific one of them red. If your change makes nothing
red, you have not tested it yet.
