package jfx.editor.plugins

import jfx.editor.Editor

def basePlugin(body: BasePlugin ?=> Unit = {})(using editor: Editor): BasePlugin =
  BasePlugin.basePlugin(body)

def headingPlugin(body: HeadingPlugin ?=> Unit = {})(using editor: Editor): HeadingPlugin =
  HeadingPlugin.headingPlugin(body)

def listPlugin(body: ListPlugin ?=> Unit = {})(using editor: Editor): ListPlugin =
  ListPlugin.listPlugin(body)

def linkPlugin(body: LinkPlugin ?=> Unit = {})(using editor: Editor): LinkPlugin =
  LinkPlugin.linkPlugin(body)

def imagePlugin(body: ImagePlugin ?=> Unit = {})(using editor: Editor): ImagePlugin =
  ImagePlugin.imagePlugin(body)

def tablePlugin(body: TablePlugin ?=> Unit = {})(using editor: Editor): TablePlugin =
  TablePlugin.tablePlugin(body)

def codePlugin(body: CodePlugin ?=> Unit = {})(using editor: Editor): CodePlugin =
  CodePlugin.codePlugin(body)

def horizontalRulePlugin(body: HorizontalRulePlugin ?=> Unit = {})(using
    editor: Editor
): HorizontalRulePlugin =
  HorizontalRulePlugin.horizontalRulePlugin(body)
