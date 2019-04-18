
name := "JsaKcp"

version := "0.1"

scalaVersion := "2.12.8"

lazy val root = (project in file(".")).settings(
	name := "JsaKcp",
	version := "0.1",
	scalaVersion := "2.12.8",
	
	libraryDependencies ++= Seq(
		"org.apache.commons" % "commons-vfs2-distribution" % "2.3",
		"ch.qos.logback" % "logback-parent" % "1.2.3",
		"com.typesafe" % "config" % "1.3.3",
		"org.slf4j" % "slf4j-api" % "1.8.0-beta4"
	),
	
	assemblyJarName in assembly := s"${name.value}-${version.value}.jar",
)

publishTo := {
	val nexus = "https://oss.sonatype.org/"
	if (isSnapshot.value)
		Some("snapshots" at nexus + "content/repositories/snapshots")
	else
		Some("releases"  at nexus + "service/local/staging/deploy/maven2/")
}