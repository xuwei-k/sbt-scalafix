package scalafix.internal.sbt

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import org.scalatest.FunSuite
import scalafix.sbt.ScalafixPlugin
import scala.collection.JavaConverters._
import sbt._
import scalafix.interfaces.ScalafixError

class ScalafixAPISuite extends FunSuite {
  test("ScalafixPlugin.cli") {
    val baos = new ByteArrayOutputStream()
    val logger = sbt.internal.util.ConsoleLogger(new PrintStream(baos))
    val (api, args) = ScalafixPlugin.classloadScalafixAPI(
      logger,
      List(
        "com.geirsson" % "example-scalafix-rule_2.12" % "1.2.0"
      )
    )
    val tmp = Files.createTempFile("scalafix", "Tmp.scala")
    tmp.toFile.deleteOnExit()
    Files.write(
      tmp,
      """
        |object A {
        |  val x = 1;
        |}
        |""".stripMargin.getBytes()
    )
    val obtainedError = api
      .runMain(
        args
          .withPaths(List(tmp).asJava)
          .withArgs(
            List("--settings.DisableSyntax.noSemicolons", "true").asJava
          )
          .withRules(List("SyntacticRule", "DisableSyntax").asJava) // from example-scalafix-rule
      )
      .toList
    assert(obtainedError == List(ScalafixError.LinterError))
    val obtained = new String(Files.readAllBytes(tmp))
    assert(obtained.endsWith("// v1 SyntacticRule!\n"))
    val out = fansi.Str(baos.toString()).plainText
    val obtainedOut = out.replaceFirst(".*Tmp.scala", "[error] Tmp.scala")
    assert(
      obtainedOut.trim ==
        """|[error] Tmp.scala:3:12: error: semicolons are disabled
           |[error]   val x = 1;
           |[error]            ^
           |""".stripMargin.trim
    )
  }
}
