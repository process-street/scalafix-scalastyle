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

/** A parameter as the rule needs it: its name, whether an argument can be
  * passed to it by name in an ordinary argument list, and the symbol of its
  * declared type, when that type is a plain reference.
  */
final case class Parameter(
    name: String,
    isImplicit: Boolean,
    isRepeated: Boolean,
    typeSymbol: Option[String]
)

/** Parameter lists of the methods and constructors defined in the project
  * being fixed, and which of those methods are extension methods, read from
  * the SemanticDB files its compiler wrote.
  *
  * Scalafix looks a symbol up in the classfiles on the classpath. That works
  * for Scala 2, whose classfiles carry Scala signatures, but a Scala 3
  * classfile carries only TASTy, so on Scala 3 a symbol defined in any other
  * file has no information. This index fills that gap from the SemanticDB
  * files themselves. It covers only the project's own code: a library
  * compiled by Scala 3 ships no SemanticDB.
  *
  * It is built once, on the first lookup that needs it.
  */
final class SemanticdbIndex(roots: => List[Path]) {
  private lazy val index: SemanticdbIndex.Entries = SemanticdbIndex.load(roots)

  def method(symbol: String): Option[List[List[Parameter]]] =
    index.methods.get(symbol)

  def constructors(cls: String): List[List[List[Parameter]]] =
    index.constructors.getOrElse(cls, Nil).flatMap(method)

  /** Whether the method is one whose parameter lists the rule cannot line up
    * with a call.
    *
    * A Scala 3 extension method is one: SemanticDB does not mark it, but its
    * receiver is the only parameter written before the method's name, so its
    * first parameter is defined before the method is. A method with no
    * definition at all is one too, such as an `export` forwarder, which may
    * forward an extension method. The `apply` and `copy` a case class gets
    * have no definition either, and are fine.
    */
  def isUnplaced(symbol: String): Boolean =
    index.unplaced.contains(symbol)
}

object SemanticdbIndex {
  val empty: SemanticdbIndex = new SemanticdbIndex(Nil)

  private final case class Entries(
      methods: Map[String, List[List[Parameter]]],
      constructors: Map[String, List[String]],
      unplaced: Set[String]
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
    val unplaced = mutable.Set.empty[String]

    def add(document: s.TextDocument): Unit = {
      val infos = document.symbols.iterator.map(info => info.symbol -> info).toMap
      lazy val definedAt = document.occurrences.iterator
        .filter(_.role.isDefinition)
        .flatMap(occurrence => occurrence.range.map(range => occurrence.symbol -> ((range.startLine, range.startCharacter))))
        .toMap
      document.symbols.foreach { info =>
        val isMethod = info.kind.isMethod || info.kind.isConstructor
        if (isMethod && info.symbol.isGlobal && !info.language.isJava) {
          info.signature match {
            case signature: s.MethodSignature =>
              methods(info.symbol) = signature.parameterLists.toList.map(parameters(_, infos))
              if (info.kind.isConstructor) {
                val owner = info.symbol.owner
                constructors(owner) = info.symbol :: constructors.getOrElse(owner, Nil)
              } else {
                val receiver = signature.parameterLists.headOption.flatMap(_.symlinks.headOption)
                definedAt.get(info.symbol) match {
                  case Some(methodAt) =>
                    val isExtension = receiver.flatMap(definedAt.get).exists(Ordering[(Int, Int)].lt(_, methodAt))
                    if (isExtension) unplaced += info.symbol
                  case None =>
                    if (info.displayName != "apply" && info.displayName != "copy") unplaced += info.symbol
                }
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

    Entries(methods.toMap, constructors.toMap, unplaced.toSet)
  }

  private def parameters(
      scope: s.Scope,
      infos: Map[String, s.SymbolInformation]
  ): List[Parameter] =
    (scope.symlinks.flatMap(infos.get) ++ scope.hardlinks).toList.map { info =>
      val tpe = info.signature match {
        case s.ValueSignature(tpe) => Some(tpe)
        case _ => None
      }
      Parameter(
        name = info.displayName,
        isImplicit = has(info, s.SymbolInformation.Property.IMPLICIT) ||
          has(info, s.SymbolInformation.Property.GIVEN),
        isRepeated = tpe.exists(_.isInstanceOf[s.RepeatedType]),
        typeSymbol = tpe.flatMap(typeSymbol)
      )
    }

  private def typeSymbol(tpe: s.Type): Option[String] =
    tpe match {
      case s.TypeRef(_, symbol, _) => Some(symbol)
      case s.ByNameType(inner) => typeSymbol(inner)
      case s.AnnotatedType(_, inner) => typeSymbol(inner)
      case _ => None
    }

  private def has(info: s.SymbolInformation, property: s.SymbolInformation.Property): Boolean =
    (info.properties & property.value) != 0
}
