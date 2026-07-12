(ns kotoba.crm.leadscore-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.crm.leadscore :as leadscore]))

(deftest point-values-are-the-documented-spec
  (is (= {:email-open         1
          :email-click        3
          :pricing-page-view  5
          :form-fill          10
          :demo-request       20}
         leadscore/point-values)))

(deftest recompute-score-sums-recognized-events
  (testing "single event"
    (is (= 1 (leadscore/recompute-score [{:kind :email-open}]))))
  (testing "mixed history"
    (is (= 39 (leadscore/recompute-score
               [{:kind :email-open}
                {:kind :email-click}
                {:kind :pricing-page-view}
                {:kind :form-fill}
                {:kind :demo-request}]))))
  (testing "repeated events all count"
    (is (= 3 (leadscore/recompute-score
              [{:kind :email-open} {:kind :email-open} {:kind :email-open}])))))

(deftest recompute-score-handles-empty-and-nil-history-as-zero
  (is (= 0 (leadscore/recompute-score [])))
  (is (= 0 (leadscore/recompute-score nil))))

(deftest recompute-score-ignores-unrecognized-event-kinds
  (is (= 1 (leadscore/recompute-score
            [{:kind :email-open} {:kind :webinar-attend-unknown-kind}])))
  (testing "but they are surfaced, not swallowed entirely"
    (is (= [{:kind :webinar-attend-unknown-kind}]
           (leadscore/unrecognized-events
            [{:kind :email-open} {:kind :webinar-attend-unknown-kind}])))
    (is (= [] (leadscore/unrecognized-events [{:kind :email-open}])))
    (is (= [] (leadscore/unrecognized-events nil)))))

(deftest recompute-score-decay-is-opt-in-and-symmetric-guarded
  (let [history [{:kind :demo-request :date {:year 2026 :month 1 :day 1}}]]
    (testing "no decay args -> plain undecayed sum"
      (is (= 20 (leadscore/recompute-score history))))
    (testing "zero elapsed days -> full weight"
      (is (= 20 (leadscore/recompute-score
                 history {:as-of-date {:year 2026 :month 1 :day 1}
                          :decay-half-life-days 30}))))
    (testing "one half-life elapsed -> half weight, rounded"
      (is (= 10 (leadscore/recompute-score
                 history {:as-of-date {:year 2026 :month 1 :day 31}
                          :decay-half-life-days 30}))))
    (testing "half-life without as-of-date -> nil, no silent default"
      (is (nil? (leadscore/recompute-score
                 history {:decay-half-life-days 30}))))
    (testing "as-of-date without half-life -> nil, no silent default"
      (is (nil? (leadscore/recompute-score
                 history {:as-of-date {:year 2026 :month 1 :day 31}}))))))

(deftest mismatch-flags-deviation-beyond-tolerance
  (let [history [{:kind :form-fill} {:kind :demo-request}]] ; recomputes to 30
    (testing "exact match -> nil (no opinion)"
      (is (nil? (leadscore/mismatch history 30))))
    (testing "beyond default zero tolerance -> diagnostic map"
      (let [m (leadscore/mismatch history 45)]
        (is (some? m))
        (is (= 30 (:recomputed m)))
        (is (= 45 (:proposed m)))
        (is (= 15 (:delta m)))))
    (testing "within an explicit nonzero tolerance -> nil"
      (is (nil? (leadscore/mismatch history 32 2))))
    (testing "beyond an explicit nonzero tolerance -> diagnostic map"
      (is (some? (leadscore/mismatch history 33 2))))))

(deftest coverage-is-honest-not-aspirational
  (let [c (leadscore/coverage)]
    (is (= :fixed-weighted-point (:model c)))
    (is (= leadscore/point-values (:point-values c)))
    (is (string? (:note c)))))
