package jfx.bridge

import jfx.core.component.AbstractComponent
import jfx.core.dsl.DslLayer
import jfx.core.dsl.DslLayer.render
import jfx.core.dsl.EventDsl.on
import jfx.core.render.Cursor
import jfx.core.state.{
  CompositeDisposable,
  Disposable => CoreDisposable,
  ListProperty => CoreListProperty,
  Property => CoreProperty
}
import jfx.forms.*
import jfx.forms.Form.FormContext
import jfx.forms.validators.{Validator, ValidatorFactory}
import org.scalajs.dom
import reflect.Annotation

import scala.collection.mutable
import scala.scalajs.js

/** Step 5/6 of JAVASCRIPT_API.md §9 ("Router-Fassade, dann Forms-Schema" / "Komponentenregistratur
  * auffüllen"): the forms facade -- the trigger from CLAUDE_REVIEW_3.md §5 was
  * "`@anjunar/jfx-controls` exists AND a form-schema projection is designed" (settled: a TS-native
  * validator schema, see `npm/jfx-forms/src/validators.ts`).
  *
  * The one real design problem this file solves: `jfx.forms.Form`/`SubForm` bind a control to a
  * model property by asking a macro-built `reflect.ClassDescriptor` for the accessor named
  * `control.name` -- and there is no such descriptor for a plain TypeScript object, because there is
  * no Scala case class behind it. `DynamicFormular` below is the alternative binding strategy: a TS
  * model is a plain `Record<string, Property<T> | ListProperty<T>>`, so the control's model property
  * is found by dynamic key lookup instead of macro reflection -- no `ClassDescriptor` involved. This
  * mirrors `Formular.bind`/`bindNow` almost line for line; it does not touch `Formular.scala` itself,
  * so the 285+ tests on the `ClassDescriptor` path are undisturbed.
  *
  * Validators have the same shape of problem and the same shape of answer: `ValidatorFactory` (in
  * `jfx-forms`) already dispatches on `reflect.Annotation(annotationClassName, parameters)` at
  * runtime, not at macro time -- `npm/jfx-forms/src/validators.ts` builds exactly that shape as plain
  * data (`notNull()`, `size(1, 100)`, ...), and `FormFactories.schemaFrom` turns it into real
  * `Annotation` values that the *same*, unmodified `ValidatorFactory`/`BuiltinValidators` consume. No
  * validator logic is ported to TypeScript.
  *
  * `ArrayForm`, `FieldSet`, `ComboBox`, `ImageCropper`, `Input`, `InputContainer` need none of this --
  * they have no `ClassDescriptor` dependency at all, so their factories register the existing Scala
  * classes directly, exactly like `ControlFactories`/`ViewportFactories` do.
  *
  * What is not projected in this pass: `SubForm`'s `newInstance()`/`clearForm()`/`factory` (an
  * imperative reinstantiation hook -- the facade is reactive-input only, so a JS consumer wanting a
  * fresh instance mounts a new `subForm` under a `when()` instead), `ComboBox`'s `valueRenderer`/
  * `footerRenderer`/`identityBy`/`selectionText`/`dropdownWidth`/`dropdownHeight`/`rowHeight`, and a
  * dynamic model that swaps its whole shape after the control tree is built (binding happens once, at
  * registration -- matching `Formular`'s own per-control bind, which likewise never re-resolves the
  * accessor after the first successful bind). Each has an obvious trigger to add later.
  */
private[bridge] object FormFactories {

  /** A model field, resolved dynamically by control name. `PropertyHandle`/`ListPropertyHandle` are
    * the concrete bridge classes (not the `JsReadOnlyProperty` duck type) because binding needs the
    * genuine two-way Scala `Property`/`ListProperty`, not a read-only wrapper -- the same reason
    * `ControlFactories.source` special-cases `ListPropertyHandle` instead of going through
    * `ReactiveBridge`.
    */
  def resolveModelProperty(model: js.Dictionary[js.Any], name: String): Option[Any] =
    model.get(name).collect {
      case handle: PropertyHandle[?]     => handle.underlyingProperty
      case handle: ListPropertyHandle[?] => handle.underlyingList
    }

  /** `{ fieldName: [{ name, parameters }] }` from `npm/jfx-forms/src/validators.ts` -> real
    * `Annotation`s, the same value `ValidatorFactory.createValidators` already knows how to read off
    * a macro-built `PropertyDescriptor`.
    */
  def schemaFrom(options: js.Dictionary[js.Any]): Map[String, Array[Annotation]] =
    options.get("schema") match {
      case Some(raw) =>
        raw
          .asInstanceOf[js.Dictionary[js.Array[ValidatorSpecFacade]]]
          .toMap
          .view
          .mapValues(specs => specs.map(toAnnotation).toArray)
          .toMap
      case None => Map.empty
    }

  private def toAnnotation(spec: ValidatorSpecFacade): Annotation =
    Annotation(spec.name, spec.parameters.toMap)

  /** `Int => (AbstractComponent, Cursor) ?=> Control[?]` for `ArrayForm.Renderer`, from a JS
    * `(index, scope) => void` that mounts its item through the DSL. `ArrayForm.compose` sets
    * `currentIndex` before calling this, so the item's own self-registration (`Input.compose`'s
    * `controller.register(this)`, same as any other control under a `FormController`) already lands
    * in `ArrayForm.mountedByIndex` by the time `render` returns below -- `itemControlAt` reads that
    * back instead of requiring the JS body to construct and return a `Control[?]` value, which the
    * `(scope) => void` shape the rest of this bridge uses cannot express.
    *
    * `self` is a thunk, not a value: this renderer has to exist *before* the `ArrayForm` it reads
    * from does, because `ArrayFormFactory` passes it to the constructor (`initialRenderer`) so the
    * very first `compose` sees a renderer already -- see that constructor parameter's doc comment for
    * why setting it after `compose` (`controlRenderer_=`, the ordinary DSL path) is a real hydration
    * bug, not just a style difference.
    */
  def arrayFormRenderer(
      name: String,
      self: () => ArrayForm[js.Any],
      render: js.Function1[Int, js.Function1[ScopeHandleBridge, Unit]]
  ): ArrayForm.Renderer =
    (index: Int) =>
      (parent: AbstractComponent) ?=>
        (cursor: Cursor) ?=> {
          render(index)(new ScopeHandleBridge(parent, cursor))
          self().itemControlAt(index).getOrElse {
            throw new IllegalStateException(
              s"arrayForm '$name' item renderer for index $index did not mount a control."
            )
          }
        }
}

@js.native
private[bridge] trait ValidatorSpecFacade extends js.Object {
  val name: String                      = js.native
  val parameters: js.Dictionary[js.Any] = js.native
}

/** Converts between `jfx.forms.Media` (a Scala class of `Property`-wrapped fields, built for the
  * Scala.js-only `ImageCropper` UI) and the plain JSON-shaped value a TS model field can actually
  * hold. Every other control's value type is already JS-compatible end to end (`js.Any`, `String`);
  * `Media` is the one exception, so it is the one place this bridge translates a value instead of
  * passing it through.
  */
private[bridge] object MediaCodec {

  def toJs(media: Media): js.Any =
    if (media == null) null
    else
      js.Dictionary[js.Any](
        "id"          -> media.id.get.toString,
        "name"        -> media.name.get,
        "contentType" -> media.contentType.get,
        "data"        -> media.data.get,
        "thumbnail"   -> Option(media.thumbnail.get).map(thumbnailToJs).orNull
      )

  private def thumbnailToJs(thumbnail: Thumbnail): js.Any =
    js.Dictionary[js.Any](
      "id"          -> thumbnail.id.get.toString,
      "name"        -> thumbnail.name.get,
      "contentType" -> thumbnail.contentType.get,
      "data"        -> thumbnail.data.get
    )

  def fromJs(value: js.Any): Media =
    if (value == null || js.isUndefined(value)) null
    else {
      val dict = value.asInstanceOf[js.Dictionary[js.Any]]
      new Media(
        id = CoreProperty(java.util.UUID.fromString(stringField(dict, "id"))),
        name = CoreProperty(stringField(dict, "name")),
        contentType = CoreProperty(stringField(dict, "contentType")),
        data = CoreProperty(stringField(dict, "data")),
        thumbnail = CoreProperty(dict.get("thumbnail")
          .filter(value => value != null && !js.isUndefined(value)).map(thumbnailFromJs).orNull)
      )
    }

  private def thumbnailFromJs(value: js.Any): Thumbnail = {
    val dict = value.asInstanceOf[js.Dictionary[js.Any]]
    new Thumbnail(
      id = CoreProperty(java.util.UUID.fromString(stringField(dict, "id"))),
      name = CoreProperty(stringField(dict, "name")),
      contentType = CoreProperty(stringField(dict, "contentType")),
      data = CoreProperty(stringField(dict, "data"))
    )
  }

  private def stringField(dict: js.Dictionary[js.Any], key: String): String =
    dict.get(key).map(_.asInstanceOf[String]).getOrElse("")

  /** Same shape as `jfx.core.state.Property.subscribeBidirectional`, translating at each edge instead
    * of passing the value straight through.
    */
  def subscribeBidirectional(jsProperty: CoreProperty[js.Any], media: CoreProperty[Media]): CoreDisposable = {
    var syncing = false

    // The model is authoritative on initial binding. Neither conversion may
    // echo back into it, otherwise an empty control erases an existing image.
    media.set(fromJs(jsProperty.get))

    val fromMedia = media.observeWithoutInitial { value =>
      if (!syncing) {
        syncing = true
        try jsProperty.set(toJs(value))
        finally syncing = false
      }
    }
    val fromJsProp = jsProperty.observeWithoutInitial { value =>
      if (!syncing) {
        syncing = true
        try media.set(fromJs(value))
        finally syncing = false
      }
    }

    CoreDisposable {
      fromMedia.dispose()
      fromJsProp.dispose()
    }
  }
}

/** The dynamic counterpart of `jfx.forms.Formular[M]`: same field bookkeeping, error grouping and
  * lifecycle, but a control's model property is found by name in a `js.Dictionary` instead of through
  * a `ClassDescriptor`, and its validators come from a JS-supplied schema instead of annotations on a
  * Scala case class. See the file-level doc comment for why this exists as a sibling trait rather
  * than a change to `Formular.scala`.
  */
private[bridge] trait DynamicFormular extends FormController { self: AbstractComponent & Editable =>

  def formModel: js.Dictionary[js.Any]
  def formSchema: Map[String, Array[Annotation]]

  val controls: CoreListProperty[Control[?]] = CoreListProperty()

  private val fieldsByName       = mutable.LinkedHashMap.empty[String, Control[?]]
  private val bindingsByControl  = mutable.Map.empty[Control[?], CoreDisposable]
  private val unboundControls    = mutable.LinkedHashMap.empty[String, String]

  override def register(control: Control[?]): Unit = {
    fieldsByName.get(control.name) match {
      case Some(current) if current eq control => return
      case Some(current)                       => unregister(current)
      case None                                => ()
    }

    fieldsByName.put(control.name, control)
    controls += control
    bindNow(control)
    control.editableProperty.set(self.editableProperty.get)
  }

  override def unregister(control: Control[?]): Unit =
    fieldsByName.get(control.name).filter(_ eq control).foreach { _ =>
      fieldsByName.remove(control.name)
      unboundControls.remove(control.name)
      bindingsByControl.remove(control).foreach(_.dispose())
      val index = controls.indexWhere(_ eq control)
      if (index >= 0) controls.remove(index)
    }

  /** Every control that could not find a model property, by name -> reason. Mirrors
    * `Formular.validateBindings`.
    */
  def validateBindings(): Seq[String] =
    unboundControls.values.toSeq ++ controls.toSeq.flatMap {
      case nested: FormController => nested.validateBindings()
      case _                        => Seq.empty
    }

  def validate(): Seq[String] = controls.toSeq.flatMap(_.validate(forceVisible = true))

  /** Rebinds every child against the current model dictionary. This matters for
    * nested forms whose parent Property replaces the whole model object.
    */
  protected def rebindModel(): Unit =
    controls.toSeq.foreach { control =>
      bindingsByControl.remove(control).foreach(_.dispose())
      bindNow(control)
    }

  override def clearErrors(): Unit =
    controls.foreach { control =>
      control.setErrors(Nil)
      control match {
        case nested: FormController => nested.clearErrors()
        case _                      => ()
      }
    }

  override def resetInteractionState(): Unit =
    controls.foreach { control =>
      control.setDirty(false)
      control.setFocused(false)
      control.setErrors(Nil)
      control match {
        case nested: FormController => nested.resetInteractionState()
        case _                      => ()
      }
    }

  def setErrorResponses(responses: Seq[ErrorResponse]): Unit =
    responses
      .filter(_.path.nonEmpty)
      .groupBy(_.path.head)
      .foreach { case (fieldName, errors) =>
        fieldsByName.get(fieldName).foreach {
          case nested: FormController => nested.setErrorResponses(errors.map(_.withoutHead))
          case control                  => control.setErrors(errors.map(_.message))
        }
      }

  private def bindNow(control: Control[?]): Unit = {
    val annotations      = formSchema.getOrElse(control.name, Array.empty[Annotation])
    val rawValidators     = control.validators.asInstanceOf[CoreListProperty[Validator[Any]]]
    val addedValidators   = ValidatorFactory.createValidators(annotations)
    addedValidators.foreach(rawValidators += _)

    val binding: CoreDisposable =
      FormFactories.resolveModelProperty(formModel, control.name) match {
        case Some(source) =>
          (control, source, control.valueProperty) match {
            case (_: ImageCropper, s: CoreProperty[Any @unchecked], t: CoreProperty[Any @unchecked]) =>
              unboundControls.remove(control.name)
              MediaCodec.subscribeBidirectional(
                s.asInstanceOf[CoreProperty[js.Any]],
                t.asInstanceOf[CoreProperty[Media]]
              )
            case (_, s: CoreProperty[Any @unchecked], t: CoreProperty[Any @unchecked]) =>
              unboundControls.remove(control.name)
              CoreProperty.subscribeBidirectional(s, t)
            case (_, s: CoreListProperty[Any @unchecked], t: CoreListProperty[Any @unchecked]) =>
              unboundControls.remove(control.name)
              CoreListProperty.subscribeBidirectional(s, t)
            case _ =>
              failBinding(
                control,
                s"model property '${control.name}' does not pair with the control's value type"
              )
              CoreDisposable.empty
          }
        case None =>
          failBinding(control, s"no property named '${control.name}' on the form model")
          CoreDisposable.empty
      }

    bindingsByControl.put(
      control,
      CoreDisposable {
        binding.dispose()
        addedValidators.foreach { validator =>
          val index = rawValidators.indexWhere(_ == validator)
          if (index >= 0) rawValidators.remove(index)
        }
      }
    )
  }

  private def failBinding(control: Control[?], reason: String): Unit = {
    val message = s"Form cannot bind control '${control.name}': $reason."
    unboundControls.put(control.name, message)
    dom.console.error(message)
  }
}

/** `form` -- the root of a dynamically bound form. Mirrors `jfx.forms.Form`, minus the
  * `ClassDescriptor` it cannot have.
  */
private[bridge] final class DynamicForm(
    val formModel: js.Dictionary[js.Any],
    val formSchema: Map[String, Array[Annotation]],
    formName: String
) extends AbstractComponent,
      Editable,
      DynamicFormular {

  val tagName = "form"

  override def prefix: String = formName

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      FormContext.provide(this)
      addDisposable(editableProperty.observe { editable =>
        controls.foreach(_.editableProperty.set(editable))
      })
      on("submit")(_.preventDefault())
    }
}

/** `sub-form` -- a nested, dynamically bound `<fieldset>` that is itself a `Control` of its parent
  * form, the same relationship `jfx.forms.SubForm` has to `Form`. Bound once, at registration, to
  * whatever the parent model held under this name -- matching `Formular`'s own per-control bind,
  * which never re-resolves after the first successful bind either. `newInstance`/`clearForm` are not
  * projected (see the file-level doc comment); a JS consumer wanting a fresh nested model mounts a new
  * `subForm` under `when()` instead.
  */
private[bridge] final class DynamicSubForm(
    val name: String,
    initialModel: js.Dictionary[js.Any],
    val formSchema: Map[String, Array[Annotation]],
    standalone: Boolean
) extends AbstractComponent,
      Control[js.Dictionary[js.Any]],
      Editable,
      DynamicFormular {

  val tagName = "fieldset"

  override val valueProperty: CoreProperty[js.Dictionary[js.Any]] = CoreProperty(initialModel)

  private var contextPrefix: String = name

  override def prefix: String = contextPrefix

  override def formModel: js.Dictionary[js.Any] =
    Option(valueProperty.get).getOrElse(js.Dictionary())

  override def validate(forceVisible: Boolean = false): Seq[String] =
    super.validate(forceVisible) ++ controls.toSeq.flatMap(_.validate(forceVisible))

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      if (!standalone) {
        val parentController = FormContext.inject.getOrElse(
          throw new IllegalStateException(s"subForm '$name' requires a Form context.")
        )
        parentController.register(this)
        contextPrefix = s"${parentController.prefix}.$name"
        addDisposable(() => parentController.unregister(this))
      }

      setProperty("disabled", !editableProperty.get)
      addDisposable(editableProperty.observe { editable =>
        setProperty("disabled", !editable)
        controls.foreach(_.editableProperty.set(editable))
      })

      // The parent form's bidirectional binding can replace valueProperty with
      // a completely new dictionary. Re-resolve child fields on each change so
      // controls never remain attached to the old nested model.
      addDisposable(valueProperty.observeWithoutInitial { _ => rebindModel() })

      FormContext.provide(this)
    }
}

private[bridge] object FormFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val model  = options.get("model").map(_.asInstanceOf[js.Dictionary[js.Any]]).getOrElse(js.Dictionary())
    val schema = FormFactories.schemaFrom(options)
    val name   = options.get("name").map(ControlFactories.str).getOrElse("default")

    DslLayer.child(new DynamicForm(model, schema, name)) {
      val self = summon[DynamicForm]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}

private[bridge] object SubFormFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name       = ControlFactories.str(options("name"))
    val model      = options.get("model").map(_.asInstanceOf[js.Dictionary[js.Any]]).getOrElse(js.Dictionary())
    val schema     = FormFactories.schemaFrom(options)
    val standalone = options.get("standalone").map(ControlFactories.bool).getOrElse(false)

    DslLayer.child(new DynamicSubForm(name, model, schema, standalone)) {
      val self = summon[DynamicSubForm]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}

private[bridge] object InputFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name       = ControlFactories.str(options("name"))
    val standalone = options.get("standalone").map(ControlFactories.bool).getOrElse(false)

    Input.input(name, standalone) {
      val self = summon[Input]
      options.get("type").foreach(value => Input.inputType_=(ControlFactories.str(value))(using self))
      options.get("placeholder").foreach(value => self.placeholder(ControlFactories.strProp(value)))
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}

private[bridge] object InputContainerFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val label = ControlFactories.strProp(options("label"))

    InputContainer.inputContainer(label) {
      val self = summon[AbstractComponent].asInstanceOf[InputContainer]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}

private[bridge] object FieldSetFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name = ControlFactories.str(options("name"))

    FieldSet.fieldSet(name) {
      val self = summon[FieldSet]
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}

private[bridge] object ArrayFormFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name         = ControlFactories.str(options("name"))
    val standalone   = options.get("standalone").map(ControlFactories.bool).getOrElse(false)
    val itemRenderer =
      options("itemRenderer").asInstanceOf[js.Function1[Int, js.Function1[ScopeHandleBridge, Unit]]]

    // Built with the renderer already in hand (`initialRenderer`), not via `controlRenderer_=`
    // after the fact -- see `ArrayForm`'s constructor and `FormFactories.arrayFormRenderer` for why
    // the two are not equivalent under SSR/hydration. `self` is assigned before `DslLayer.child`
    // calls `compose`, so the renderer thunk always sees the real instance once it runs.
    var self: ArrayForm[js.Any] = null
    val renderer                = FormFactories.arrayFormRenderer(name, () => self, itemRenderer)
    self = new ArrayForm[js.Any](name, standalone, Some(renderer))

    DslLayer.child(self) {
      body(new ComponentHandleBridge(self), new ScopeHandleBridge(self, summon[Cursor]))
    }
  }
}

private[bridge] object ComboBoxFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name       = ControlFactories.str(options("name"))
    val standalone = options.get("standalone").map(ControlFactories.bool).getOrElse(false)

    ComboBox.comboBox[js.Any](name, standalone) {
      val self = summon[ComboBox[js.Any]]

      options.get("items").foreach {
        case handle: ListPropertyHandle[?] =>
          val list = handle.underlyingList.asInstanceOf[CoreListProperty[js.Any]]
          self.itemsProperty.setAll(list.toSeq)
          self.addDisposable(list.observeChanges(_ => self.itemsProperty.setAll(list.toSeq)))
        case value =>
          self.itemsProperty.setAll(value.asInstanceOf[js.Array[js.Any]].toSeq)
      }

      options.get("placeholder").foreach(value => self.placeholder(ControlFactories.strProp(value)))
      options.get("multiSelect").foreach(value =>
        ComboBox.multiSelect_=(ControlFactories.bool(value))(using self)
      )
      options.get("converter").foreach { value =>
        val convert = value.asInstanceOf[js.Function1[js.Any, String]]
        ComboBox.converter_=[js.Any](using self)(item => convert(item))
      }
      options.get("itemRenderer").foreach { value =>
        val render = value.asInstanceOf[
          js.Function2[js.Any, ReadOnlyPropertyHandle[Boolean], js.Function1[ScopeHandleBridge, Unit]]
        ]
        ComboBox.itemRenderer[js.Any](using self) { (item, selected) =>
          (p: AbstractComponent) ?=>
            (c: Cursor) ?=>
              render(item, new ReadOnlyPropertyHandle(selected))(new ScopeHandleBridge(p, c))
        }
      }
    }
  }
}

private[bridge] object ImageCropperFactory extends ComponentFactory {
  override def mount(
      options: js.Dictionary[js.Any],
      body: js.Function2[ComponentHandleBridge, ScopeHandleBridge, Unit]
  )(using parent: AbstractComponent, cursor: Cursor): AbstractComponent = {
    val name       = ControlFactories.str(options("name"))
    val standalone = options.get("standalone").map(ControlFactories.bool).getOrElse(false)

    ImageCropper.imageCropper(name, standalone) {
      val self = summon[ImageCropper]
      options.get("placeholder").foreach(value => self.placeholder(ControlFactories.strProp(value)))
      options.get("aspectRatio").foreach(value =>
        ImageCropper.aspectRatio_=(ControlFactories.dbl(value))(using self)
      )
      options.get("outputType").foreach(value =>
        ImageCropper.outputType_=(ControlFactories.str(value))(using self)
      )
      options.get("outputQuality").foreach(value =>
        ImageCropper.outputQuality_=(ControlFactories.dbl(value))(using self)
      )
      options.get("windowTitle").foreach(value =>
        ImageCropper.windowTitle_=(ControlFactories.str(value))(using self, summon)
      )
    }
  }
}
