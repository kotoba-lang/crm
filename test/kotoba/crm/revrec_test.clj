(ns kotoba.crm.revrec-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.crm.revrec :as revrec]))

(def sub
  {:contract-value-usd 12000.0
   :term-months 12
   :start-date {:year 2026 :month 1 :day 1}})

(deftest elapsed-months-clamps-to-term
  (is (= 0 (revrec/elapsed-months (assoc sub :as-of-date {:year 2026 :month 1}))))
  (is (= 6 (revrec/elapsed-months (assoc sub :as-of-date {:year 2026 :month 7}))))
  (is (= 12 (revrec/elapsed-months (assoc sub :as-of-date {:year 2027 :month 6})))
      "clamped, never exceeds term-months"))

(deftest recognized-revenue-to-date-is-straight-line
  (is (= 6000.0 (revrec/recognized-revenue-to-date
                 (assoc sub :as-of-date {:year 2026 :month 7}))))
  (is (= 1000.0 (revrec/recognized-revenue-to-date
                 (assoc sub :as-of-date {:year 2026 :month 2})))))

(deftest recognized-revenue-to-date-refuses-non-positive-term
  (is (nil? (revrec/recognized-revenue-to-date
             (assoc sub :term-months 0 :as-of-date {:year 2026 :month 7}))))
  (is (nil? (revrec/recognized-revenue-to-date
             (assoc sub :term-months nil :as-of-date {:year 2026 :month 7})))))

(deftest mismatch-flags-deviation-beyond-tolerance
  (let [s (assoc sub :as-of-date {:year 2026 :month 7})]
    (testing "within tolerance -> nil (no opinion)"
      (is (nil? (revrec/mismatch s 6000.00))))
    (testing "beyond tolerance -> diagnostic map with real spec-basis"
      (let [m (revrec/mismatch s 9000.0)]
        (is (some? m))
        (is (= 6000.0 (:recomputed-usd m)))
        (is (= 9000.0 (:proposed-usd m)))
        (is (= revrec/spec-basis (:spec-basis m)))))))

(deftest coverage-is-honest-not-aspirational
  (let [c (revrec/coverage)]
    (is (= :straight-line-only (:model c)))
    (is (= #{:fasb-asc-606 :iasb-ifrs-15} (:spec-basis c)))))
