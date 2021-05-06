name := """model-viewer"""
version       := "1.0"

scalaVersion  := "2.13.0"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += Resolver.jcenterRepo

resolvers += Resolver.bintrayRepo("unibas-gravis", "maven")

libraryDependencies += "ch.unibas.cs.gravis" %% "scalismo-faces" % "0.90.0"

mainClass in assembly := Some("faces.apps.ModelViewer")

assemblyJarName in assembly := "model-viewer.jar"
