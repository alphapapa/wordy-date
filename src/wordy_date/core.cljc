(ns wordy-date.core
  (:require [clojure.string :as str]
            [instaparse.core :as insta]
            #?(:clj  [clj-time.core :as t]
               :cljs [cljs-time.core :as t])
            #?(:clj [clojure.edn])))

(def wordy-date-parser (insta/parser
                        (str/join "\n" ["S = pos-duration | dow | quickie"
                                        "quickie = 'tomorrow' | 'now'"

                                        ;; durations
                                        "pos-duration = _duration <(',' | <whitespace> 'and')?> (<whitespace> _duration)*"
                                        "_duration = (<pre-superfluous> <whitespace>)? digits <whitespace> period"
                                        "<pre-superfluous> = 'in' | '+' | 'plus'"

                                        "period = #'(sec(ond)?|min(ute)?|day|hour|week|month|year)s?'"
                                        "dow = long-days | short-days"

                                        "short-days = 'mon' | 'tue' | 'wed' | 'thur' | 'fri' | 'sat' | 'sun'"
                                        "long-days = 'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday'"

                                        "whitespace = #'\\s+'"
                                        "digits = #'-?[0-9]+'"])
                        :string-ci true))

(defn handle-pos-duration [& args]
  (reduce (fn [now [_ amount f]]
            (t/plus now (f amount))) (t/now) args))

(defn handle-dow [dow]
  (let [now (t/now)
        our-dow (t/day-of-week now)]
    (if (< our-dow dow)
      ;; this occurs this week as the day given is "after" now
      (t/plus now (t/days (- dow our-dow)))

      ;; we move to next week as the day asked for is "before" now
      (let [next-week (t/plus (t/now) (t/weeks 1))
            our-dow (t/day-of-week next-week)]
        (t/plus next-week (t/days (- dow our-dow)))))))

(defn day-number* [day]
  (case (subs day 0 3)
    "mon" 1
    "tue" 2
    "wed" 3
    "thu" 4
    "fri" 5
    "sat" 6
    "sun" 7))

(def day-number (memoize day-number*))

(defn parse [st]
  (let [S (insta/transform {:digits #?(:clj clojure.edn/read-string
                                       :cljs js/parseInt)
                            :period #(case (subs % 0 3)
                                       "sec" t/seconds
                                       "min" t/minutes
                                       "day" t/days
                                       "hou" t/hours
                                       "wee" t/weeks
                                       "mon" t/months
                                       "yea" t/years)
                            :long-days day-number
                            :short-days day-number
                            :pos-duration handle-pos-duration
                            :dow handle-dow
                            :quickie (fn [s]
                                       (case s
                                         "tomorrow" (t/plus (t/now) (t/days 1))
                                         "now" (t/now)))}
                           (wordy-date-parser st))]
    (when (= (first S) :S)
      (second S))))
