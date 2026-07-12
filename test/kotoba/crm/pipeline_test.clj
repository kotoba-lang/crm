(ns kotoba.crm.pipeline-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.crm.pipeline :as pipeline]))

(def stages [:prospecting :qualification :proposal :negotiation :closed-won])
(def exits #{:closed-lost})

(deftest stage-rank-finds-index
  (is (= 0 (pipeline/stage-rank stages :prospecting)))
  (is (= 4 (pipeline/stage-rank stages :closed-won)))
  (is (nil? (pipeline/stage-rank stages :nope))))

(deftest terminal-stages-includes-last-and-exits
  (is (= #{:closed-won :closed-lost} (pipeline/terminal-stages stages exits))))

(deftest valid-transition?-allows-immediate-next-only
  (testing "immediate next stage is valid"
    (is (pipeline/valid-transition? stages exits :prospecting :qualification)))
  (testing "skipping ahead is invalid"
    (is (not (pipeline/valid-transition? stages exits :prospecting :negotiation)))
    (is (not (pipeline/valid-transition? stages exits :prospecting :closed-won))))
  (testing "exit stage is reachable from any non-terminal stage"
    (is (pipeline/valid-transition? stages exits :prospecting :closed-lost))
    (is (pipeline/valid-transition? stages exits :negotiation :closed-lost)))
  (testing "no transition is valid from an already-terminal stage"
    (is (not (pipeline/valid-transition? stages exits :closed-won :qualification)))
    (is (not (pipeline/valid-transition? stages exits :closed-lost :prospecting)))))

(deftest next-stages-reports-immediate-plus-exits
  (is (= #{:qualification :closed-lost} (pipeline/next-stages stages exits :prospecting)))
  (is (= #{} (pipeline/next-stages stages exits :closed-won))))
