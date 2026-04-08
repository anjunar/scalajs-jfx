package jfx.core.meta

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ClassLoaderIntegrationSpec extends AnyFlatSpec with Matchers {

  "PackageClassLoader" should "access registered classes from app module at runtime" in {
    // Arrange: DomainRegistry.init() wurde in Main.scala aufgerufen
    // Hier simulieren wir das für den Test
    
    val coreLoader = PackageClassLoader("app.domain.core")
    coreLoader.register(() => new jfx.test.TestEntity(), classOf[jfx.test.TestEntity])
    
    // Act: Klasse laden
    val descriptor = coreLoader.loadClass("jfx.test.TestEntity")
    
    // Assert: Descriptor gefunden
    descriptor shouldBe defined
    descriptor.get.typeName shouldBe "jfx.test.TestEntity"
  }

  it should "create instances of registered classes" in {
    // Arrange
    val coreLoader = PackageClassLoader("app.domain.core")
    coreLoader.register(() => new jfx.test.TestEntity(), classOf[jfx.test.TestEntity])
    
    // Act: Instanz erstellen
    val instance = coreLoader.createInstance("jfx.test.TestEntity")
    
    // Assert: Instanz erstellt
    instance shouldBe defined
    instance.get shouldBe a[jfx.test.TestEntity]
  }

  it should "list all registered classes" in {
    // Arrange
    val loader = PackageClassLoader("app.domain.test")
    loader.register(() => new jfx.test.TestEntity(), classOf[jfx.test.TestEntity])
    loader.register(() => new jfx.test.TestComponent(), classOf[jfx.test.TestComponent])
    
    // Act: Alle registrierten Klassen holen
    val allClasses = loader.getAllRegistered
    
    // Assert: Alle Klassen gefunden
    allClasses.size shouldBe 2
    allClasses.map(_.simpleName) should contain allOf ("TestEntity", "TestComponent")
  }

  it should "find subtypes" in {
    // Arrange
    val loader = PackageClassLoader("app.domain.test")
    loader.register(() => new jfx.test.TestEntity(), classOf[jfx.test.TestEntity])
    loader.register(() => new jfx.test.TestComponent(), classOf[jfx.test.TestComponent])
    
    // Act: Subtypes von TestBase finden
    val subTypes = loader.getSubTypes("jfx.test.TestBase")
    
    // Assert: Beide Klassen gefunden
    subTypes.map(_.simpleName) should contain allOf ("TestEntity", "TestComponent")
  }

  "Reflect (high-level API)" should "access registered classes" in {
    // Arrange
    Reflect.register(() => new jfx.test.TestEntity())
    
    // Act: Klasse laden
    val descriptor = Reflect.loadClass("jfx.test.TestEntity")
    
    // Assert
    descriptor shouldBe defined
  }

  it should "create instances via high-level API" in {
    // Arrange
    Reflect.register(() => new jfx.test.TestEntity())
    
    // Act
    val instance = Reflect.createInstance[jfx.test.TestEntity]("jfx.test.TestEntity")
    
    // Assert
    instance shouldBe defined
  }
}
