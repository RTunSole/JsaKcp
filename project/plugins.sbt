addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

//指定下载地址
//resolvers += Resolver.url("bintray-sbt-plugins", url("http://dl.bintray.com/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

logLevel := Level.Warn

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.5")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0-M1") // fot sbt-0.13.5 or higher