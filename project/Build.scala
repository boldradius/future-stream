import _root_.sbtrelease.ReleasePlugin.ReleaseKeys._
import bintray.Plugin._
import sbt._
import sbt.Keys._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import sbtrelease.ReleasePlugin._

object ApplicationBuild extends Build {
  val scala11Version = "2.11.7"

  val main = Project("future-stream", file(".")).settings(
    useGlobalVersion := false,
    scalaVersion := scala11Version,
    organization := "com.boldradius",
    crossScalaVersions := Seq("2.10.4", scala11Version),
    publishMavenStyle := true,
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    parallelExecution in Test := false,
    bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("boldradiussolutions"),
    libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  ).settings(bintraySettings ++ releaseSettings: _*)

  val kafka = Project("kafka", file("kafka")).dependsOn(main).settings(
    scalaVersion := scala11Version,
    libraryDependencies += "org.apache.kafka" %% "kafka" % "0.8.2.2",
    //libraryDependencies += "org.apache.kafka" % "kafka-clients" % "0.8.2.2",   // TODO: this should be a 2.11 dependency
    resolvers += Resolver.bintrayRepo("boldradiussolutions", "maven"),
    bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("boldradiussolutions"))
}