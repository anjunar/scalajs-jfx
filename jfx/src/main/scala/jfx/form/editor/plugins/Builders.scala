package jfx.form.editor.plugins

def basePlugin(init: BasePlugin ?=> Unit = {}): BasePlugin =
  BasePlugin.basePlugin(init)

def headingPlugin(init: HeadingPlugin ?=> Unit = {}): HeadingPlugin =
  HeadingPlugin.headingPlugin(init)

def listPlugin(init: ListPlugin ?=> Unit = {}): ListPlugin =
  ListPlugin.listPlugin(init)

def linkPlugin(init: LinkPlugin ?=> Unit = {}): LinkPlugin =
  LinkPlugin.linkPlugin(init)

def imagePlugin(init: ImagePlugin ?=> Unit = {}): ImagePlugin =
  ImagePlugin.imagePlugin(init)
