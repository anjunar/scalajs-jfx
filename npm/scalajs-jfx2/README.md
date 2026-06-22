# @anjunar/scalajs-jfx

NPM companion package for the Maven artifact `com.anjunar::scalajs-jfx`.

This package provides the default JFX component CSS.

Install it next to the Scala.js dependency:

```bash
npm install @anjunar/scalajs-jfx
```

Import the shared Anjunar UI grammar once in your application stylesheet, then import the JFX package CSS from your Vite entrypoint:

```css
@import "tailwindcss";
@import "@anjunar/ui";
```

```javascript
import '@anjunar/scalajs-jfx/index.css'
import 'scalajs:main.js'
```

Or import the package root if you want the default CSS side effect:

```javascript
import '@anjunar/scalajs-jfx'
```
