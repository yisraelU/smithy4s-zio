import sbt.*
import sbt.Keys.*

object BuildPlugin extends AutoPlugin {

  val scalaVersionSuffix = Def
    .setting {
      scalaBinaryVersion.value match {
        case "2.11" => Seq("-2", "-2.11")
        case "2.12" => Seq("-2", "-2.12")
        case "2.13" => Seq("-2", "-2.13")
        case _      => Seq("-3")
      }
    }

  lazy val compilerPlugins = Seq(
    libraryDependencies ++= {
      if (scalaVersion.value.startsWith("2."))
        Seq(
          compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
          compilerPlugin(
            "org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full
          )
        )
      else Seq.empty
    }
  )
}
