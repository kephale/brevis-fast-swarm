# brevis-fast-swarm
A simple swarm in Brevis

# Run with:

[Leiningen](http://leiningen.org)  

lein run -m brevis-fast-swarm.core

# Stashing some speed numbers:

Note that these are really quite variable and super dirty, +/- 0.25 or so is normalish for that duration of sampling.

## native core.matrix

Sim time: 50.0 Time: 165.96872 FPS: 0.3012615

## vectorz-clj

Sim time: 50.0 Time: 23.564976 FPS: 2.1217878

## clatrix

Sim time: 50.0 Time: 45.65343 FPS: 1.0952066

## brevis-simple-swarm

Sim time: 50.0 Time: 12.915947 FPS: 3.8711686