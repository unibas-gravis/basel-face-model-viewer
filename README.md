 # Simple Morphable Model Viewer
 
 Simple tool to view the [Basel Face Model 2017](http://faces.cs.unibas.ch/bfm/bfm2017.html).
 For further information about the model and the surrounding theory and software please visit [http://gravis.dmi.unibas.ch/PMM](http://gravis.dmi.unibas.ch/PMM)

## Requirements
- installed [Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (Version 8.0 or higher recommended)
- installed [sbt](http://www.scala-sbt.org/release/tutorial/Setup.html) (only for compiling from sources)

## Run:
- download `model-viewer.jar` under `release`
- run `java -jar model-viewer.jar -Xmx2g`

## Usage:
- upper random and reset button will update color/shape/expression parameters for active tab
- lower random and reset button will update all model parameters
- sliders are ordered according to the principal components
- press `Ctrl` to move pose with mouse (first click on face to activate the frame)
 
## Installation (only needed for adapting code):
- clone repository
- clone project
- compile and run `sbt run -mem 2000`

## Maintainer

- Bernhard Egger <bernhard.egger@unibas.ch>
- Andreas Morel-Forster <andreas.forster@unibas.ch>

## Dependencies

- [scalismo-faces](https://github.com/unibas-gravis/scalismo-faces) `0.5.+`
