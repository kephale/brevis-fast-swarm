(ns brevis-fast-swarm.core
  (:gen-class)
  (:use [brevis.graphics.basic-3D]
        [brevis.physics collision core space utils]
        [brevis.shape box sphere cone]
        [brevis core osd vector camera utils display image])
  (:require [clojure.core.matrix :as mat]
            [clojure.core.matrix.operators :as matops]))

(mat/set-current-implementation :vectorz)
;(mat/set-current-implementation :clatrix)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Swarm
;;
;; Swarm simulations are models of flocking behavior in collections of organisms.   
;;
;; For reference see:
;;
;;   Reynolds, Craig W. "Flocks, herds and schools: A distributed behavioral model." ACM SIGGRAPH Computer Graphics. Vol. 21. No. 4. ACM, 1987.
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Globals

(def num-birds (atom 1000))

(def avoidance-distance (atom 25))
(def boundary (atom 300))

(def max-velocity (atom 5))
(def max-acceleration (atom 10))

(def max-distance 10000000000) ; this isnt a constraint, but like max_integer

(def swarm (atom nil))
(def start-time (atom (System/nanoTime)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Birds

(defn random-bird-position
  "Returns a random valid bird position."
  [] 
  [(- (rand @boundary) (/ @boundary 2)) 
   (- (rand @boundary) (/ @boundary 2)) 
   (- (rand @boundary) (/ @boundary 2))])
  
(defn periodic-boundary
  "Change a position according to periodic boundary conditions."
  [pos]
  (let [x (x-val pos)
        y (y-val pos)
        z (z-val pos)]
    (vec3 (cond (> x @boundary) (- (mod x @boundary) @boundary)
                (< x (- @boundary)) (mod (- x) @boundary)
                :else x)
          (cond (> y @boundary) (- (mod y @boundary) @boundary)
                (< y (- @boundary)) (mod (- y) @boundary)
                :else y)
          (cond (> z @boundary) (- (mod z @boundary) @boundary)
                (< z (- @boundary)) (mod (- z) @boundary)
                :else z))))

(defn bound-acceleration
  "Keeps the acceleration within a reasonable range."
  [v]  
  (if (> (length v) @max-acceleration)
    (mul (div v (length v)) @max-acceleration)
    v))

(defn update-birds-from-swarm
  "Update the information from birds based on the current data in swarm."
  []
  (let [birds (all-objects)]
    (doseq [bird birds]
      (set-object (get-uid bird)
                  (set-acceleration ;(move bird (apply vec3 (mat/get-row (:positions @swarm) (:swarm-id bird))))
                                    (let [bird-pos (get-position bird)]
                                      (if (or (> (java.lang.Math/abs (x-val bird-pos)) @boundary) 
                                              (> (java.lang.Math/abs (y-val bird-pos)) @boundary) 
                                              (> (java.lang.Math/abs (z-val bird-pos)) @boundary)) 
                                        (move bird (periodic-boundary bird-pos) #_(vec3 0 25 0))
                                        bird))
                                    (bound-acceleration (apply vec3 (mat/get-row (:accelerations @swarm) (:swarm-id bird)))))))))

(defn update-swarm-from-birds
  "Update the information from birds based on the current data in swarm."
  []
  (let [birds (all-objects)]
    (swap! swarm assoc
           :positions
           (mat/matrix (into []
                             (for [bird birds]
                               (vec3-to-seq (get-position bird))))))
    #_(doseq [bird birds]
       (mat/set-row (:positions @swarm) (:swarm-id bird) 
                 (vec3-to-seq (get-position bird)))
       #_(set-row! (:acceleration @swarm) (:swarm-id bird) 
                  (vec3-to-seq (get-acceleration bird)))
       )))

(defn make-swarm
  "Make a swarm of birds."
  [num-birds]
  (reset! swarm {:positions
                 (mat/matrix
                   (into []
                         (repeatedly num-birds
                                     random-bird-position)))
                 :velocities
                 (mat/matrix
                   (into []
                         (repeat num-birds
                                 [0 0 0])))
                 :accelerations
                 (mat/matrix
                   (into []
                         (repeat num-birds
                                 [0 0 0])))})
  (dotimes [k num-birds]
    (add-object (assoc (move (make-real {:type :bird
                                         :color (vec4 1 0 0 1)
                                         :shape (create-cone 10.2 1.5)})
                             (apply vec3 (mat/get-row (:positions @swarm) k)))
                       :swarm-id k)))
  )


#_(defn bound-velocity
   "Keeps the acceleration within a reasonable range."
   [v]  
   (if (> (length v) @max-velocity)
     (mul (div v (length v)) @max-velocity)
     v))

#_(defn fly
   "Change the acceleration of a bird."
   [bird]
   (let [bird-pos (get-position bird)
        
         closest-bird (get-closest-neighbor bird)
        
         new-acceleration (if-not closest-bird
                            ;; No neighbor, move randomly
                            (elmul (vec3 (- (rand) 0.5) (- (rand) 0.5) (- (rand) 0.5))
                                   (mul bird-pos -1.0))
                            (let [dvec (sub bird-pos (get-position closest-bird)) 
                                  len (length dvec)]
                              (add (sub (get-velocity closest-bird) (get-velocity bird)); velocity matching
                                   (if (<= len @avoidance-distance)
                                     ;; If far from neighbor, get closer
                                     dvec
                                     ;; If too close to neighbor, move away
                                     (add (mul dvec -1.0)
                                          (vec3 (rand 0.1) (rand 0.1) (rand 0.1)))))));; add a small random delta so we don't get into a loop                                    
         new-acceleration (if (zero? (length new-acceleration))
                            new-acceleration
                            (mul new-acceleration (/ 1 (length new-acceleration))))]    
     (set-velocity
       (set-acceleration
         (if (or (> (java.lang.Math/abs (x-val bird-pos)) @boundary) 
                 (> (java.lang.Math/abs (y-val bird-pos)) @boundary) 
                 (> (java.lang.Math/abs (z-val bird-pos)) @boundary)) 
           (move bird (periodic-boundary bird-pos) #_(vec3 0 25 0))
           bird)
         (bound-acceleration new-acceleration))
       (bound-velocity (get-velocity bird)))))

(enable-kinematics-update :bird); This tells the simulator to move our objects
#_(add-update-handler :bird fly); This tells the simulator how to update these objects

(defn swarm-fly
  "Update a whole swarm."
  []
  (println "Sim time:" (get-time) "Time:" (float (/ (- (System/nanoTime) @start-time) 1000000000)) "FPS:" (float (/ (get-time) (float (/ (- (System/nanoTime) @start-time) 1000000000)) )))
  (update-swarm-from-birds)
  (let [birds (all-objects)
        num-birds (count birds)
        vpositions (map #(mat/get-row (:positions @swarm) %) (range num-birds))
        closest-neighbors-idx-dists (for [bidx (range num-birds)]
                                      (let [bpos (nth vpositions bidx)]
                                        (apply (partial min-key second)
                                               (map-indexed #(vector %1 (if (= bidx %1)
                                                                          max-distance; Max distance along diagonal
                                                                          (mat/distance bpos %2)))
                                                            vpositions))))
        dvec-mat (matops/- (mat/matrix (into []
                                             (map #(mat/get-row (:positions @swarm) (first %))
                                                  closest-neighbors-idx-dists)))
                            (:positions @swarm))
        weights (map #((if (<= (second %) @avoidance-distance) - identity); use if to determine the function to use
                        (if (zero? (second %)) 1 (/ (second %)))) closest-neighbors-idx-dists)]
    (swap! swarm assoc
           :accelerations 
           (mat/matrix (into []
                             (for [idx (range num-birds)]
                               (mat/scale (mat/get-row dvec-mat idx) (nth weights idx))))))
    #_(dotimes [idx num-birds]
       (mat/set-row (:accelerations @swarm) idx
                     (mat/scale (mat/get-row dvec-mat idx) (nth weights idx)))))
  (update-birds-from-swarm))           

(add-global-update-handler -10 swarm-fly)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Collision handling
;;
;; Collision functions take [collider collidee] and return [collider collidee]
;; This is only called once per pair of colliding objects.

(defn bump
  "Collision between two birds."
  [bird1 bird2]  
  [(set-color bird1 (vec4 (rand) (rand) (rand) 1))
   bird2])

;(add-collision-handler :bird :bird bump)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## brevis control code

(defn initialize-simulation
  "This is the function where you add everything to the world."
  []  
  (init-world)
  (init-view)  

  (set-camera-information (vec3 -10.0 57.939613 -890.0) (vec4 1.0 0.0 0.0 0.0))
  
  (set-dt 1)
  (set-neighborhood-radius 50)
  (default-display-text)
  (make-swarm @num-birds)
  #_(dotimes [_ @num-birds]
     (add-object (random-bird))))

;; Start zee macheen
(defn -main [& args]
  (if-not (empty? args)
    (start-nogui initialize-simulation)
    (start-gui initialize-simulation)))

(autostart-in-repl -main)
