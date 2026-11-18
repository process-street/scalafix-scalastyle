package fix

import org.scalatest.funsuite.AnyFunSuiteLike
import scalafix.testkit.AbstractSyntacticRuleSuite

class ParameterShadowingCheckerSuite
    extends AbstractSyntacticRuleSuite
    with AnyFunSuiteLike {
  private val rule = new ParameterShadowingChecker()

  // The case that motivated the rule: two branches merged cleanly, one adding
  // the objectRef parameter and the other a local objectRef for the task, so
  // comment and attachment webhooks silently sent the task's ID.
  check(
    rule,
    "ParameterShadowingChecker local val in a for/yield block of an override",
    """
      |package foobar
      |
      |class WebhookEventsServiceImpl extends WebhookEventsService {
      |  override def sendTaskEventToWebhooks(
      |      triggerType: WebhookTriggerType,
      |      task: Task,
      |      objectRef: WebhookObjectReference
      |  ): Future[Unit] =
      |    for {
      |      webhooks <- webhookRepository.findByTrigger(triggerType)
      |    } yield {
      |      val objectRef = WebhookObjectReference(id = task.id, objectType = WebhookObjectType.Task)
      |      webhooks.foreach(send(_, objectRef))
      |    }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class WebhookEventsServiceImpl extends WebhookEventsService {
      |  override def sendTaskEventToWebhooks(
      |      triggerType: WebhookTriggerType,
      |      task: Task,
      |      objectRef: WebhookObjectReference
      |  ): Future[Unit] =
      |    for {
      |      webhooks <- webhookRepository.findByTrigger(triggerType)
      |    } yield {
      |      val objectRef/* scalafix:ok */ = WebhookObjectReference(id = task.id, objectType = WebhookObjectType.Task)
      |      webhooks.foreach(send(_, objectRef))
      |    }
      |}
      |""".stripMargin
  )

  check(
    rule,
    "ParameterShadowingChecker value binding in a for",
    """
      |package foobar
      |
      |object Foo {
      |  def a(objectRef: Ref, tasks: List[Task]): List[Ref] =
      |    for {
      |      task <- tasks
      |      objectRef = Ref(task.id)
      |    } yield objectRef
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |object Foo {
      |  def a(objectRef: Ref, tasks: List[Task]): List[Ref] =
      |    for {
      |      task <- tasks
      |      objectRef/* scalafix:ok */ = Ref(task.id)
      |    } yield objectRef
      |}
      |""".stripMargin
  )

  check(
    rule,
    "ParameterShadowingChecker local val, var, lazy val and def",
    """
      |package foobar
      |
      |object Foo {
      |  def a(x: Int): Int = {
      |    val x = 1
      |    x
      |  }
      |
      |  def b(x: Int): Int = {
      |    var x = 1
      |    x
      |  }
      |
      |  def c(x: Int): Int = {
      |    lazy val x = 1
      |    x
      |  }
      |
      |  def d(x: Int): Int = {
      |    def x: Int = 1
      |    x
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |object Foo {
      |  def a(x: Int): Int = {
      |    val x/* scalafix:ok */ = 1
      |    x
      |  }
      |
      |  def b(x: Int): Int = {
      |    var x/* scalafix:ok */ = 1
      |    x
      |  }
      |
      |  def c(x: Int): Int = {
      |    lazy val x/* scalafix:ok */ = 1
      |    x
      |  }
      |
      |  def d(x: Int): Int = {
      |    def x/* scalafix:ok */: Int = 1
      |    x
      |  }
      |}
      |""".stripMargin
  )

  check(
    rule,
    "ParameterShadowingChecker later and implicit parameter lists",
    """
      |package foobar
      |
      |object Foo {
      |  def a(x: Int)(y: Int)(implicit ec: ExecutionContext): Future[Int] = {
      |    val y = 1
      |    implicit val ec: ExecutionContext = ExecutionContext.global
      |    Future(x + y)
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |object Foo {
      |  def a(x: Int)(y: Int)(implicit ec: ExecutionContext): Future[Int] = {
      |    val y/* scalafix:ok */ = 1
      |    implicit val ec/* scalafix:ok */: ExecutionContext = ExecutionContext.global
      |    Future(x + y)
      |  }
      |}
      |""".stripMargin
  )

  // `using` clauses and extension receivers are Scala 3 syntax, which this
  // harness cannot parse: input/src/main/scala-3/fix/ParameterShadowingScala3.scala
  // covers them.

  check(
    rule,
    "ParameterShadowingChecker names bound by a val pattern",
    """
      |package foobar
      |
      |object Foo {
      |  def a(x: Int, y: Int): Int = {
      |    val (x, z) = (1, 2)
      |    val Some(y) = Option(3)
      |    x + y + z
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |object Foo {
      |  def a(x: Int, y: Int): Int = {
      |    val (x/* scalafix:ok */, z) = (1, 2)
      |    val Some(y/* scalafix:ok */) = Option(3)
      |    x + y + z
      |  }
      |}
      |""".stripMargin
  )

  check(
    rule,
    "ParameterShadowingChecker locals inside lambdas, cases and nested methods",
    """
      |package foobar
      |
      |object Foo {
      |  def a(task: Task, tasks: List[Task]): Unit = {
      |    tasks.foreach { t =>
      |      val task = t
      |      println(task)
      |    }
      |    tasks.headOption match {
      |      case Some(t) =>
      |        val tasks = List(t)
      |        println(tasks)
      |      case None =>
      |    }
      |    def helper(n: Int): Int = {
      |      val task = n
      |      task
      |    }
      |    def tasks: Int = 0
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |object Foo {
      |  def a(task: Task, tasks: List[Task]): Unit = {
      |    tasks.foreach { t =>
      |      val task/* scalafix:ok */ = t
      |      println(task)
      |    }
      |    tasks.headOption match {
      |      case Some(t) =>
      |        val tasks/* scalafix:ok */ = List(t)
      |        println(tasks)
      |      case None =>
      |    }
      |    def helper(n: Int): Int = {
      |      val task/* scalafix:ok */ = n
      |      task
      |    }
      |    def tasks/* scalafix:ok */: Int = 0
      |  }
      |}
      |""".stripMargin
  )

  check(
    rule,
    "ParameterShadowingChecker lambda parameters, case binders and generators are fine",
    """
      |package foobar
      |
      |object Foo {
      |  def a(task: Task, tasks: List[Task]): Unit = {
      |    tasks.map { task => task.id }
      |    tasks.map(task => task.id)
      |    tasks.map { (task: Task) => task.id }
      |    tasks.collect { case task if task.done => task.id }
      |    Option(task) match {
      |      case Some(task) => println(task)
      |      case None =>
      |    }
      |    for {
      |      task <- tasks
      |      if task.done
      |    } yield task.id
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |object Foo {
      |  def a(task: Task, tasks: List[Task]): Unit = {
      |    tasks.map { task => task.id }
      |    tasks.map(task => task.id)
      |    tasks.map { (task: Task) => task.id }
      |    tasks.collect { case task if task.done => task.id }
      |    Option(task) match {
      |      case Some(task) => println(task)
      |      case None =>
      |    }
      |    for {
      |      task <- tasks
      |      if task.done
      |    } yield task.id
      |  }
      |}
      |""".stripMargin
  )

  check(
    rule,
    "ParameterShadowingChecker nested method parameters and class members are fine",
    """
      |package foobar
      |
      |class Foo(task: Task) {
      |  val name: String = "foo"
      |
      |  def a(name: String): Int = name.length
      |
      |  def b(task: Task): Runnable = {
      |    def helper(task: Task): Unit = println(task)
      |    class Local {
      |      val task: Int = 1
      |      def run(): Unit = println(task)
      |    }
      |    new Runnable {
      |      val task: Int = 2
      |      def run(): Unit = helper(Foo.this.task)
      |    }
      |  }
      |
      |  def c(x: Int): Int = {
      |    val y = x + 1
      |    y
      |  }
      |}
      |""".stripMargin,
    """
      |package foobar
      |
      |class Foo(task: Task) {
      |  val name: String = "foo"
      |
      |  def a(name: String): Int = name.length
      |
      |  def b(task: Task): Runnable = {
      |    def helper(task: Task): Unit = println(task)
      |    class Local {
      |      val task: Int = 1
      |      def run(): Unit = println(task)
      |    }
      |    new Runnable {
      |      val task: Int = 2
      |      def run(): Unit = helper(Foo.this.task)
      |    }
      |  }
      |
      |  def c(x: Int): Int = {
      |    val y = x + 1
      |    y
      |  }
      |}
      |""".stripMargin
  )
}
