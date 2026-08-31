package jfx.json

import jfx.core.state.{ListProperty, Property}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import reflect.{ClassDescriptor, ReflectClassLoader}
import reflect.macros.ReflectMacros

import java.util.UUID
import scala.annotation.meta.field
import scala.collection.immutable.ListMap
import scala.reflect.ClassTag
import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal

class JsonMapperSpec extends AnyFlatSpec with Matchers {

  "JsonMapper annotations" should "serialize and deserialize JsonProperty field names" in {
    val mapper = JsonMapper()
    val person = AnnotatedPerson()
    person.name.set("Ada")
    person.age.set(37)

    val json = mapper.serialize(person, JsonMapperSpec.annotatedPersonMeta)

    json.selectDynamic("fullName").asInstanceOf[String] shouldBe "Ada"
    js.isUndefined(json.selectDynamic("name")) shouldBe true
    json.selectDynamic("age").asInstanceOf[Double].toInt shouldBe 37

    val restored = mapper.deserialize[AnnotatedPerson](
      literal(fullName = "Grace", age = 41),
      JsonMapperSpec.annotatedPersonMeta
    )

    restored.name.get shouldBe "Grace"
    restored.age.get shouldBe 41
  }

  it should "provide the inline companion API for registered model metadata" in {
    val person = AnnotatedPerson()
    person.name.set("Inline")
    person.age.set(21)

    val json     = JsonMapper.serialize(person)
    val restored = JsonMapper.deserialize[AnnotatedPerson](json)

    restored.name.get shouldBe "Inline"
    restored.age.get shouldBe 21
  }

  it should "ignore JsonIgnore properties in both directions by default" in {
    val mapper = JsonMapper()
    val user   = IgnoredSecret()
    user.visible.set("public")
    user.secret.set("private")

    val json = mapper.serialize(user, JsonMapperSpec.ignoredSecretMeta)

    json.selectDynamic("visible").asInstanceOf[String] shouldBe "public"
    js.isUndefined(json.selectDynamic("secret")) shouldBe true

    val restored = mapper.deserialize[IgnoredSecret](
      literal(visible = "client", secret = "tampered"),
      JsonMapperSpec.ignoredSecretMeta
    )

    restored.visible.get shouldBe "client"
    restored.secret.get shouldBe ""
  }

  it should "support directional JsonIgnore properties" in {
    val mapper = JsonMapper()
    val model  = DirectionalIgnore()
    model.readOnly.set("server-value")
    model.writeOnly.set("server-secret")

    val json = mapper.serialize(model, JsonMapperSpec.directionalIgnoreMeta)

    json.selectDynamic("readOnly").asInstanceOf[String] shouldBe "server-value"
    js.isUndefined(json.selectDynamic("writeOnly")) shouldBe true

    val restored = mapper.deserialize[DirectionalIgnore](
      literal(readOnly = "client-value", writeOnly = "client-secret"),
      JsonMapperSpec.directionalIgnoreMeta
    )

    restored.readOnly.get shouldBe ""
    restored.writeOnly.get shouldBe "client-secret"
  }

  "JsonMapper polymorphism" should "use JsonType values in both directions" in {
    val mapper = JsonMapper()
    val circle = Circle()
    circle.radius.set(12)

    val json = mapper.serialize(circle, JsonMapperSpec.shapeMeta)

    json.selectDynamic("@type").asInstanceOf[String] shouldBe "circle"
    json.selectDynamic("radius").asInstanceOf[Double].toInt shouldBe 12

    val restored = mapper.deserialize[Shape](
      literal(`@type` = "circle", radius = 9),
      JsonMapperSpec.shapeMeta
    )

    restored shouldBe a[Circle]
    restored.asInstanceOf[Circle].radius.get shouldBe 9
  }

  it should "reject unknown and missing types for abstract models" in {
    val mapper = JsonMapper()

    val missing = intercept[IllegalArgumentException] {
      mapper.deserialize[Shape](literal(radius = 9), JsonMapperSpec.shapeMeta)
    }
    missing.getMessage should include("Missing @type")

    val unknown = intercept[IllegalArgumentException] {
      mapper.deserialize[Shape](
        literal(`@type` = "triangle", radius = 9),
        JsonMapperSpec.shapeMeta
      )
    }
    unknown.getMessage should include("Unknown @type 'triangle'")
  }

  "JsonMapper state values" should "serialize mutated ListProperty contents" in {
    val mapper = JsonMapper()
    val thread = CommentThread()
    val reply  = Reply()
    reply.text.set("Hallo")
    thread.replies.addOne(reply)

    val json    = mapper.serialize(thread, JsonMapperSpec.commentThreadMeta)
    val replies = json.selectDynamic("replies").asInstanceOf[js.Array[js.Dynamic]]

    replies.length shouldBe 1
    replies(0).selectDynamic("text").asInstanceOf[String] shouldBe "Hallo"
  }

  it should "include only dirty nested payload plus JsonId fields" in {
    val mapper  = JsonMapper()
    val profile = Profile()
    profile.id.set("user-1")
    profile.info.get.id.set("info-1")
    profile.info.get.firstName.set("Patrick1")
    profile.info.get.firstName.setDefault("Patrick1")
    profile.info.get.lastName.set("Tester")
    profile.info.get.lastName.setDefault("Tester")
    profile.info.get.firstName.set("Patrick")

    val json = mapper.serialize(profile, JsonMapperSpec.profileMeta)
    val info = json.selectDynamic("info").asInstanceOf[js.Dynamic]

    json.selectDynamic("id").asInstanceOf[String] shouldBe "user-1"
    info.selectDynamic("id").asInstanceOf[String] shouldBe "info-1"
    info.selectDynamic("firstName").asInstanceOf[String] shouldBe "Patrick"
    js.isUndefined(info.selectDynamic("lastName")) shouldBe true
  }

  it should "reset Property and ListProperty defaults after deserialization" in {
    val mapper   = JsonMapper()
    val restored = mapper.deserialize[CommentThread](
      literal(replies = js.Array(literal(text = "stable"))),
      JsonMapperSpec.commentThreadMeta
    )

    restored.replies.isDirty shouldBe false
    restored.replies.head.text.get shouldBe "stable"
    restored.replies.head.text.isDirty shouldBe false
  }

  "JsonMapper value types" should "roundtrip options, maps, collections, UUIDs, and raw JSON" in {
    val mapper = JsonMapper()
    val id     = UUID.fromString("92707f9f-a861-4d45-9d4b-47832fe06741")
    val model  = ValueTypes()
    model.optional.set(Some("present"))
    model.identifiers.set(ListMap("primary" -> id))
    model.numbers.set(List(1, 2, 3))
    model.array.set(Array(4, 5, 6))
    model.jsArray.set(js.Array(7, 8, 9))
    model.flags.set(Set(true, false))
    model.longNumber.set(9007199254740991L)
    model.floatNumber.set(1.25f)
    model.shortNumber.set(12.toShort)
    model.byteNumber.set(3.toByte)
    model.character.set('J')
    model.raw.set(literal(nested = "untouched"))
    val reply = Reply()
    reply.text.set("array item")
    model.replies.set(Array(reply))

    val json     = mapper.serialize(model, JsonMapperSpec.valueTypesMeta)
    val restored = mapper.deserialize[ValueTypes](json, JsonMapperSpec.valueTypesMeta)

    restored.optional.get shouldBe Some("present")
    restored.identifiers.get shouldBe ListMap("primary" -> id)
    restored.numbers.get shouldBe List(1, 2, 3)
    restored.array.get.toSeq shouldBe Seq(4, 5, 6)
    restored.jsArray.get.toSeq shouldBe Seq(7, 8, 9)
    restored.flags.get shouldBe Set(true, false)
    restored.longNumber.get shouldBe 9007199254740991L
    restored.floatNumber.get shouldBe 1.25f
    restored.shortNumber.get shouldBe 12.toShort
    restored.byteNumber.get shouldBe 3.toByte
    restored.character.get shouldBe 'J'
    restored.replies.get.map(_.text.get).toSeq shouldBe Seq("array item")
    restored.raw.get
      .asInstanceOf[js.Dynamic]
      .selectDynamic("nested")
      .asInstanceOf[String] shouldBe "untouched"
  }

  it should "map a single map property as an inline JSON object" in {
    val mapper = JsonMapper()
    val labels = Labels(Map("de" -> "Hallo", "en" -> "Hello"))

    val json = mapper.serialize(labels, JsonMapperSpec.labelsMeta)

    json.selectDynamic("de").asInstanceOf[String] shouldBe "Hallo"
    js.isUndefined(json.selectDynamic("values")) shouldBe true

    val restored = mapper.deserialize[Labels](
      literal(de = "Guten Tag", en = "Hello"),
      JsonMapperSpec.labelsMeta
    )
    restored.values shouldBe Map("de" -> "Guten Tag", "en" -> "Hello")
  }

  it should "deserialize arrays and treat null arrays as empty" in {
    val mapper = JsonMapper()
    val json   = js.Array[js.Dynamic](
      literal(fullName = "Ada", age = 37),
      literal(fullName = "Grace", age = 41)
    )

    val restored = mapper.deserializeArray[AnnotatedPerson](
      json,
      JsonMapperSpec.annotatedPersonMeta
    )

    restored.map(_.name.get) shouldBe Seq("Ada", "Grace")
    mapper
      .deserializeArray[AnnotatedPerson](null, JsonMapperSpec.annotatedPersonMeta) shouldBe empty
  }

  it should "resolve generic property types from parameterized metadata" in {
    val mapper = JsonMapper()
    val model  = GenericBox[String]()
    model.value.set("typed")

    val json     = mapper.serialize(model, JsonMapperSpec.stringBoxMeta)
    val restored = mapper.deserialize[GenericBox[String]](
      json,
      JsonMapperSpec.stringBoxMeta
    )

    restored.value.get shouldBe "typed"
    restored.value.isDirty shouldBe false
  }
}

object JsonMapperSpec {
  private val loader = ReflectClassLoader.create()

  private inline def register[T](
      inline factory: () => T,
      clazz: Class[T]
  )(using ClassTag[T]): ClassDescriptor = {
    val descriptor = ReflectMacros.reflectWithAccessors[T]
    descriptor.bindRuntimeClass(clazz)
    loader.register[T](descriptor, factory)
    descriptor
  }

  val annotatedPersonMeta   = register(() => AnnotatedPerson(), classOf[AnnotatedPerson])
  val ignoredSecretMeta     = register(() => IgnoredSecret(), classOf[IgnoredSecret])
  val directionalIgnoreMeta =
    register(() => DirectionalIgnore(), classOf[DirectionalIgnore])
  val circleMeta        = register(() => Circle(), classOf[Circle])
  val replyMeta         = register(() => Reply(), classOf[Reply])
  val commentThreadMeta = register(() => CommentThread(), classOf[CommentThread])
  val nestedInfoMeta    = register(() => NestedInfo(), classOf[NestedInfo])
  val profileMeta       = register(() => Profile(), classOf[Profile])
  val valueTypesMeta    = register(() => ValueTypes(), classOf[ValueTypes])
  val labelsMeta        = register(() => Labels(), classOf[Labels])
  val genericBoxMeta    =
    register(() => GenericBox[String](), classOf[GenericBox[String]])
  val stringBoxMeta = ReflectMacros.reflectType[GenericBox[String]]

  val shapeMeta = {
    val descriptor = ReflectMacros.reflectWithAccessors[Shape]
    descriptor.bindRuntimeClass(classOf[Shape])
    loader.registerByTypeName(descriptor.typeName, descriptor)
    descriptor
  }
}

final class AnnotatedPerson(
    @(JsonProperty @field)("fullName")
    var name: Property[String] = Property(""),
    var age: Property[Int] = Property(0)
)

final class IgnoredSecret(
    var visible: Property[String] = Property(""),
    @(JsonIgnore @field)()
    var secret: Property[String] = Property("")
)

final class DirectionalIgnore(
    @(JsonIgnore @field)(serializable = true)
    var readOnly: Property[String] = Property(""),
    @(JsonIgnore @field)(deserializable = true)
    var writeOnly: Property[String] = Property("")
)

sealed abstract class Shape

@JsonType("circle")
final class Circle(
    var radius: Property[Int] = Property(0)
) extends Shape

final class Reply(
    var text: Property[String] = Property("")
)

final class CommentThread(
    var replies: ListProperty[Reply] = ListProperty()
)

final class NestedInfo(
    @(JsonId @field)()
    var id: Property[String] = Property(""),
    var firstName: Property[String] = Property(""),
    var lastName: Property[String] = Property("")
)

final class Profile(
    @(JsonId @field)()
    var id: Property[String] = Property(""),
    var info: Property[NestedInfo] = Property(NestedInfo())
)

final class ValueTypes(
    var optional: Property[Option[String]] = Property(None),
    var identifiers: Property[ListMap[String, UUID]] = Property(ListMap.empty),
    var numbers: Property[List[Int]] = Property(Nil),
    var array: Property[Array[Int]] = Property(Array.empty),
    var jsArray: Property[js.Array[Int]] = Property(js.Array()),
    var flags: Property[Set[Boolean]] = Property(Set.empty),
    var longNumber: Property[Long] = Property(0L),
    var floatNumber: Property[Float] = Property(0f),
    var shortNumber: Property[Short] = Property(0.toShort),
    var byteNumber: Property[Byte] = Property(0.toByte),
    var character: Property[Char] = Property('\u0000'),
    var raw: Property[js.Any] = Property(null),
    var replies: Property[Array[Reply]] = Property(Array.empty)
)

final class Labels(
    var values: Map[String, String] = Map.empty
)

final class GenericBox[T](
    var value: Property[T] = Property(null.asInstanceOf[T])
)
