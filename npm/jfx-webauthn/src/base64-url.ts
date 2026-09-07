/** RFC 4648 base64url conversion for WebAuthn buffer values. */

function bytesOf(value: ArrayBuffer | ArrayBufferView): Uint8Array {
  if (value instanceof ArrayBuffer) return new Uint8Array(value);

  return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
}

function base64Encode(value: Uint8Array): string {
  let binary = "";
  for (const byte of value) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function base64Decode(value: string): Uint8Array {
  try {
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
  } catch (error) {
    throw new TypeError("Invalid base64url value", { cause: error });
  }
}

function requireValid(value: string): void {
  if (value === null || value === undefined) {
    throw new TypeError("Base64url value must not be null");
  }

  const firstPadding = value.indexOf("=");
  const dataLength = firstPadding < 0 ? value.length : firstPadding;
  const padding = value.length - dataLength;
  const alphabetIsValid = /^[A-Za-z0-9_-]*$/.test(value.slice(0, dataLength));
  const paddingIsValid =
    padding <= 2 &&
    (firstPadding < 0 || /^=+$/.test(value.slice(firstPadding))) &&
    (padding === 0 || value.length % 4 === 0);

  if (!alphabetIsValid || !paddingIsValid || dataLength % 4 === 1) {
    throw new TypeError("Invalid base64url value");
  }
}

export const Base64Url = {
  encode(value: ArrayBuffer | ArrayBufferView): string {
    return base64Encode(bytesOf(value))
      .replaceAll("+", "-")
      .replaceAll("/", "_")
      .replace(/=+$/, "");
  },

  decode(value: string): ArrayBuffer {
    requireValid(value);
    const unpadded = value.slice(0, value.indexOf("=") < 0 ? value.length : value.indexOf("="));
    const standard = unpadded.replaceAll("-", "+").replaceAll("_", "/");
    const padding = (4 - (standard.length % 4)) % 4;
    return new Uint8Array(base64Decode(standard + "=".repeat(padding))).buffer as ArrayBuffer;
  },

  decodeToBytes(value: string): Uint8Array {
    return new Uint8Array(Base64Url.decode(value));
  },
} as const;

export type Base64Url = typeof Base64Url;
