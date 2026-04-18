import '@anjunar/scalajs-lexical/index.css'
import '@anjunar/scalajs-jfx/index.css'
import './style.css'
import * as scalajsApp from 'scalajs:main.js'

const isSsrMode = new URL(import.meta.url).searchParams.get('ssr') === '1'

export const renderSsr = scalajsApp.renderSsr

if (!isSsrMode) {
  scalajsApp.boot()
}
