package fix

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.{semanticdb => s}
import scala.util.Try

import scalafix.v1.Configuration

/** A parameter as the rule needs it: its name, and whether an argument can be
  * passed to it by name in an ordinary argument list.
  */
final case class Parameter(name: String, isImplicit: Boolean, isRepeated: Boolean)

/** Parameter lists of the methods and constructors defined in the project
  * being fixed, read from the SemanticDB files its compiler wrote.
  *
  * Scalafix looks a symbol up in the classfiles on the classpath. That works
  * for Scala 2, whose classfiles carry Scala signatures, but a Scala 3
  * classfile carries only TASTy, so on Scala 3 a symbol defined in any other
  * file has no information. This index fills that gap from the SemanticDB
  * files themselves.
  *
  * It is built once, on the first lookup that needs it.
  */
final class SemanticdbIndex(roots: => List[Path]) {
  private lazy val index: SemanticdbIndex.Entries = SemanticdbIndex.load(roots)

  def method(symbol: String): Option[List[List[Parameter]]] =
    index.methods.get(symbol)

  def constructors(cls: String): List[List[List[Parameter]]] =
    index.constructors.getOrElse(cls, Nil).flatMap(method)

  def applies(obj: String): List[List[List[Parameter]]] =
    index.applies.getOrElse(obj, Nil).flatMap(method)
}

object SemanticdbIndex {
  val empty: SemanticdbIndex = new SemanticdbIndex(Nil)

  private final case class Entries(
      methods: Map[String, List[List[Parameter]]],
      constructors: Map[String, List[String]],
      applies: Map[String, List[String]]
  )

  /** The SemanticDB roots of the build being fixed.
    *
    * sbt writes a configuration's SemanticDB next to its classes, to `meta`
    * beside `classes` and `test-meta` beside `test-classes`, and names only
    * the configuration's own directory in its scalac options. Test sources
    * call code in main, so every classes directory on the classpath is
    * mapped to its sibling as well.
    */
  def fromConfiguration(config: Configuration): SemanticdbIndex =
    new SemanticdbIndex(
      semanticdbRoots(config.scalacOptions, config.scalacClasspath.map(_.toNIO))
    )

  def semanticdbRoots(scalacOptions: List[String], classpath: List[Path]): List[Path] = {
    val targets = scalacOptions.zip(scalacOptions.drop(1) :+ "").collect {
      case (option, _) if option.startsWith("-semanticdb-target:") =>
        Paths.get(option.stripPrefix("-semanticdb-target:"))
      case ("-semanticdb-target", path) if path.nonEmpty =>
        Paths.get(path)
      case (option, _) if option.startsWith("-P:semanticdb:targetroot:") =>
        Paths.get(option.stripPrefix("-P:semanticdb:targetroot:"))
    }
    val directories = classpath.filter(path => Files.isDirectory(path))
    val siblings = directories.flatMap { directory =>
      Option(directory.getFileName).map(_.toString).collect {
        case name if name.endsWith("classes") =>
          directory.resolveSibling(name.stripSuffix("classes") + "meta")
      }
    }
    (targets ++ directories ++ siblings)
      .map(_.resolve("META-INF").resolve("semanticdb"))
      .filter(path => Files.isDirectory(path))
      .map(_.toAbsolutePath.normalize)
      .distinct
  }

  private def load(roots: List[Path]): Entries = {
    val methods = mutable.Map.empty[String, List[List[Parameter]]]
    val constructors = mutable.Map.empty[String, List[String]]
    val applies = mutable.Map.empty[String, List[String]]

    def add(document: s.TextDocument): Unit = {
      val infos = document.symbols.iterator.map(info => info.symbol -> info).toMap
      document.symbols.foreach { info =>
        val isMethod = info.kind.isMethod || info.kind.isConstructor
        if (isMethod && info.symbol.isGlobal && !info.language.isJava) {
          info.signature match {
            case signature: s.MethodSignature =>
              methods(info.symbol) = signature.parameterLists.toList.map(parameters(_, infos))
              val owner = info.symbol.owner
              if (info.kind.isConstructor) {
                constructors(owner) = info.symbol :: constructors.getOrElse(owner, Nil)
              } else if (info.displayName == "apply" && owner.isTerm) {
                applies(owner) = info.symbol :: applies.getOrElse(owner, Nil)
              }
            case _ =>
          }
        }
      }
    }

    roots.foreach { root =>
      val files = Files.walk(root)
      try {
        files.iterator.asScala
          .filter(_.getFileName.toString.endsWith(".semanticdb"))
          .foreach { file =>
            Try(s.TextDocuments.parseFrom(Files.readAllBytes(file))).toOption
              .foreach(_.documents.foreach(add))
          }
      } finally files.close()
    }

    Entries(methods.toMap, constructors.toMap, applies.toMap)
  }

  private def parameters(
      scope: s.Scope,
      infos: Map[String, s.SymbolInformation]
  ): List[Parameter] =
    (scope.symlinks.flatMap(infos.get) ++ scope.hardlinks).toList.map { info =>
      Parameter(
        name = info.displayName,
        isImplicit = has(info, s.SymbolInformation.Property.IMPLICIT) ||
          has(info, s.SymbolInformation.Property.GIVEN),
        isRepeated = info.signature match {
          case s.ValueSignature(_: s.RepeatedType) => true
          case _ => false
        }
      )
    }

  private def has(info: s.SymbolInformation, property: s.SymbolInformation.Property): Boolean =
    (info.properties & property.value) != 0
}
