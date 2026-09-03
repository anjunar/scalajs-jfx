import { renderSsr } from 'scalajs:main.js'

export function render(path, method, headers, assets) {
    return renderSsr(path, method, headers, assets ?? "[]")
}
