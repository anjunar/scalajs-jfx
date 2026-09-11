export function format(html: string): string {
  return html.replace(/></g, ">\n<");
}
