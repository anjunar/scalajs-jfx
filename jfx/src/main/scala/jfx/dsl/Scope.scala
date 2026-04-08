package jfx.dsl

import scala.collection.mutable
import scala.reflect.ClassTag

final class Scope (val parent: Option[Scope]) {

  import Scope.Binding
  import Scope.Lifetime
  import Scope.ServiceKey

  private val bindings = mutable.LinkedHashMap.empty[ServiceKey[?], Binding]
  private val singletonInstances = mutable.HashMap.empty[Binding, Any]
  private val scopedInstances = mutable.HashMap.empty[Binding, Any]
  private val resolutionStack: mutable.ArrayBuffer[ServiceKey[?]] =
    parent match {
      case Some(scope) => scope.resolutionStack
      case None => mutable.ArrayBuffer.empty[ServiceKey[?]]
    }

  def child(): Scope =
    new Scope(Some(this))

  def singleton[T](provider: Scope ?=> T)(using key: ServiceKey[T]): Unit =
    bind(key, Lifetime.Singleton, provider)

  def scoped[T](provider: Scope ?=> T)(using key: ServiceKey[T]): Unit =
    bind(key, Lifetime.Scoped, provider)

  def transient[T](provider: Scope ?=> T)(using key: ServiceKey[T]): Unit =
    bind(key, Lifetime.Transient, provider)

  def inject[T](using key: ServiceKey[T]): T =
    lookup(key)
      .map(resolve(_, key))
      .getOrElse(throw new java.util.NoSuchElementException(s"No service registered for ${key.label}"))
      .asInstanceOf[T]

  private def bind[T](key: ServiceKey[T], lifetime: Lifetime, provider: Scope ?=> T): Unit =
    bindings.update(key, Binding(lifetime, scope => provider(using scope), this))

  private def lookup(key: ServiceKey[?]): Option[Binding] =
    bindings.get(key).orElse(parent.flatMap(_.lookup(key)))

  private def resolve(binding: Binding, key: ServiceKey[?]): Any = {
    if (resolutionStack.contains(key)) {
      val chain = (resolutionStack.map(_.label) :+ key.label).mkString(" -> ")
      throw IllegalStateException(s"Circular dependency detected: $chain")
    }

    resolutionStack += key

    try {
      binding.lifetime match {
        case Lifetime.Singleton =>
          binding.owner.singletonInstances.getOrElseUpdate(binding, binding.provider(binding.owner))
        case Lifetime.Scoped =>
          scopedInstances.getOrElseUpdate(binding, binding.provider(this))
        case Lifetime.Transient =>
          binding.provider(this)
      }
    } finally {
      resolutionStack.remove(resolutionStack.length - 1)
    }
  }
}

object Scope {

  def scope[A](block: Scope ?=> A)(using currentScope: Scope | Null = null): A =
    val nextScope =
      if (currentScope == null) Scope.root()
      else currentScope.child()

    DslRuntime.withScope(nextScope) {
      block(using nextScope)
    }

  def singleton[T](provider: Scope ?=> T)(using scope: Scope, key: Scope.ServiceKey[T]): Unit =
    scope.singleton(provider)

  def scoped[T](provider: Scope ?=> T)(using scope: Scope, key: Scope.ServiceKey[T]): Unit =
    scope.scoped(provider)

  def transient[T](provider: Scope ?=> T)(using scope: Scope, key: Scope.ServiceKey[T]): Unit =
    scope.transient(provider)

  def inject[T](using scope: Scope, key: Scope.ServiceKey[T]): T =
    scope.inject[T]

  def root(): Scope =
    new Scope(None)

  enum Lifetime {
    case Singleton
    case Scoped
    case Transient
  }

  final case class ServiceKey[T](runtimeClass: Class[?], label: String)

  object ServiceKey {
    given [T](using classTag: ClassTag[T]): ServiceKey[T] =
      ServiceKey(classTag.runtimeClass, classTag.toString)
  }

  private final case class Binding(
    lifetime: Lifetime,
    provider: Scope => Any,
    owner: Scope
  )
}
